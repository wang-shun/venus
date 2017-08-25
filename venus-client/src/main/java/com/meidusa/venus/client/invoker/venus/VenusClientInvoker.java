package com.meidusa.venus.client.invoker.venus;

import com.meidusa.fastmark.feature.SerializerFeature;
import com.meidusa.toolkit.net.*;
import com.meidusa.toolkit.util.TimeUtil;
import com.meidusa.venus.*;
import com.meidusa.venus.annotations.Endpoint;
import com.meidusa.venus.annotations.Service;
import com.meidusa.venus.client.factory.xml.XmlServiceFactory;
import com.meidusa.venus.client.factory.xml.config.*;
import com.meidusa.venus.client.invoker.AbstractClientInvoker;
import com.meidusa.venus.client.proxy.InvokerInvocationHandler;
import com.meidusa.venus.exception.InvalidParameterException;
import com.meidusa.venus.exception.VenusExceptionFactory;
import com.meidusa.venus.extension.athena.AthenaTransactionId;
import com.meidusa.venus.io.network.AbstractBIOConnection;
import com.meidusa.venus.io.network.VenusBackendConnectionFactory;
import com.meidusa.venus.io.packet.*;
import com.meidusa.venus.io.packet.serialize.SerializeServiceRequestPacket;
import com.meidusa.venus.io.serializer.Serializer;
import com.meidusa.venus.io.serializer.SerializerFactory;
import com.meidusa.venus.metainfo.EndpointParameter;
import com.meidusa.venus.notify.InvocationListener;
import com.meidusa.venus.notify.ReferenceInvocationListener;
import com.meidusa.venus.util.VenusAnnotationUtils;
import org.apache.commons.beanutils.BeanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * venus协议服务调用实现
 * Created by Zhangzhihua on 2017/7/31.
 */
public class VenusClientInvoker extends AbstractClientInvoker implements Invoker{

    private static Logger logger = LoggerFactory.getLogger(VenusClientInvoker.class);

    private static Logger performanceLogger = LoggerFactory.getLogger("venus.client.performance");

    private static SerializerFeature[] JSON_FEATURE = new SerializerFeature[]{SerializerFeature.ShortString,SerializerFeature.IgnoreNonFieldGetter,SerializerFeature.SkipTransientField};

    private byte serializeType = PacketConstant.CONTENT_TYPE_JSON;

    private static AtomicLong sequenceId = new AtomicLong(1);

    /**
     * 远程连接配置，包含ip相关信息
     */
    private RemoteConfig remoteConfig;

    private VenusExceptionFactory venusExceptionFactory;

    private XmlServiceFactory serviceFactory;

    private boolean enableAsync = true;

    private boolean needPing = false;

    private int asyncExecutorSize = 10;

    private ConnectionConnector connector;

    private ConnectionManager connManager;

    /**
     * nio连接映射表
     */
    private Map<String, BackendConnectionPool> nioPoolMap = new HashMap<String, BackendConnectionPool>();

    //TODO 优化锁对象
    private Object lock = new Object();

    /**
     * 消息标识-请求映射表
     */
    private Map<String, Invocation> serviceInvocationMap = new ConcurrentHashMap<String, Invocation>();

    /**
     * 消息标识-响应映射表
     */
    private Map<String,AbstractServicePacket> serviceResponseMap = new ConcurrentHashMap<String, AbstractServicePacket>();

    /**
     * 调用监听容器
     */
    private InvocationListenerContainer container = new InvocationListenerContainer();

    /**
     * NIO消息响应处理
     */
    private VenusClientInvokerMessageHandler messageHandler = new VenusClientInvokerMessageHandler();

    @Override
    public void init() throws RpcException {
        if (enableAsync) {
            if (connector == null) {
                try {
                    this.connector = new ConnectionConnector("connection Connector");
                } catch (IOException e) {
                    throw new RpcException(e);
                }
                connector.setDaemon(true);

            }

            if (connManager == null) {
                try {
                    connManager = new ConnectionManager("Connection Manager", this.getAsyncExecutorSize());
                } catch (IOException e) {
                    throw new RpcException(e);
                }
                connManager.setDaemon(true);
                connManager.start();
            }

            connector.setProcessors(new ConnectionManager[]{connManager});
            connector.start();
        }

        messageHandler.setVenusExceptionFactory(venusExceptionFactory);
        messageHandler.setContainer(this.container);
        messageHandler.setLock(lock);
        messageHandler.setServiceInvocationMap(serviceInvocationMap);
        messageHandler.setServiceResponseMap(serviceResponseMap);
    }

    @Override
    public Result doInvoke(Invocation invocation, URL url) throws RpcException {
        try {
            if(!isCallbackInvocation(invocation)){
                return doInvokeWithSync(invocation, url);
            }else{
                return doInvokeWithCallback(invocation, url);
            }
        } catch (Exception e) {
            throw new RpcException(e);
        }
    }

    /**
     * 判断是否callback异步调用
     * @param invocation
     * @return
     */
    boolean isCallbackInvocation(Invocation invocation){
        EndpointParameter[] params = invocation.getParams();
        if (params != null) {
            Object[] args = invocation.getArgs();
            for (int i = 0; i < params.length; i++) {
                if (args[i] instanceof InvocationListener) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * sync同步调用
     * @param invocation
     * @param url
     * @return
     * @throws Exception
     */
    public Result doInvokeWithSync(Invocation invocation, URL url) throws Exception {
        //构造请求消息
        SerializeServiceRequestPacket request = buildRequest(invocation);
        //添加messageId -> invocation映射表
        serviceInvocationMap.put(getMessageId(request),invocation);

        //发送消息
        sendRequest(url, invocation, request);

        //阻塞等待并处理响应结果 TODO callback情况
        synchronized (lock){
            logger.info("lock wait begin...");
            lock.wait(10000);//TODO 超时时间
            logger.info("lock wait end...");
        }

        Result result = fetchResponse(getMessageId(request));
        logger.info("result:{}.",result);
        if(result == null){
            throw new RpcException(String.format("invoke timeout:%s","3000ms"));
        }
        return result;
    }

    /**
     * callback异步调用
     * @param invocation
     * @param url
     * @return
     * @throws Exception
     */
    public Result doInvokeWithCallback(Invocation invocation, URL url) throws Exception {
        //构造请求消息
        SerializeServiceRequestPacket request = buildRequest(invocation);
        //添加messageId -> invocation映射表
        serviceInvocationMap.put(getMessageId(request),invocation);

        //发送消息
        sendRequest(url, invocation, request);

        //立即返回，响应由invocationListener处理
        return new Result(null);
    }

    /**
     * 获取消息标识
     * @param request
     * @return
     */
    String getMessageId(SerializeServiceRequestPacket request){
        return String.format("%s-%s",String.valueOf(request.clientId),String.valueOf(request.clientRequestId));
    }

    /**
     * 构造请求消息
     * @param invocation
     * @return
     */
    SerializeServiceRequestPacket buildRequest(Invocation invocation){
        byte[] traceID = invocation.getTraceID();
        Method method = invocation.getMethod();
        Service service = invocation.getService();
        Endpoint endpoint = invocation.getEndpoint();
        EndpointParameter[] params = invocation.getParams();
        Object[] args = invocation.getArgs();
        String apiName = VenusAnnotationUtils.getApiname(method, service, endpoint);

        AthenaTransactionId athenaTransactionId = (AthenaTransactionId)VenusContext.get(VenusContext.ATHENA_TRANSACTION_ID);
        logger.info("athenaTransactionId:{}.",athenaTransactionId);

        Serializer serializer = SerializerFactory.getSerializer(serializeType);
        SerializeServiceRequestPacket serviceRequestPacket = new SerializeServiceRequestPacket(serializer, null);

        serviceRequestPacket.clientId = PacketConstant.VENUS_CLIENT_ID;
        serviceRequestPacket.clientRequestId = sequenceId.getAndIncrement();
        serviceRequestPacket.traceId = traceID;
        serviceRequestPacket.apiName = apiName;
        serviceRequestPacket.serviceVersion = service.version();
        serviceRequestPacket.parameterMap = new HashMap<String, Object>();

        logger.info("send request,clientId:{},clientRequestId:{}.",serviceRequestPacket.clientId,serviceRequestPacket.clientRequestId);

        if (params != null) {
            for (int i = 0; i < params.length; i++) {
                if (args[i] instanceof InvocationListener) {
                    ReferenceInvocationListener listener = new ReferenceInvocationListener();
                    ServicePacketBuffer buffer = new ServicePacketBuffer(16);
                    buffer.writeLengthCodedString(args[i].getClass().getName(), "utf-8");
                    buffer.writeInt(System.identityHashCode(args[i]));
                    listener.setIdentityData(buffer.toByteBuffer().array());
                    Type type = method.getGenericParameterTypes()[i];
                    if (type instanceof ParameterizedType) {
                        ParameterizedType genericType = ((ParameterizedType) type);
                        //TODO 兼容旧版本方案 改由invocation保存回调信息 是否允许多个listener?
                        //container.putInvocationListener((InvocationListener) args[i], genericType.getActualTypeArguments()[0]);
                        invocation.setInvocationListener((InvocationListener)args[i]);
                        invocation.setType(genericType.getActualTypeArguments()[0]);
                    } else {
                        throw new InvalidParameterException("invocationListener is not generic");
                    }

                    serviceRequestPacket.parameterMap.put(params[i].getParamName(), listener);
                } else {
                    serviceRequestPacket.parameterMap.put(params[i].getParamName(), args[i]);
                }

            }
        }
        setTransactionId(serviceRequestPacket, athenaTransactionId);
        return serviceRequestPacket;
    }

    /**
     * 发送远程调用消息
     * @param url 目标地址
     * @param invocation
     * @param serviceRequestPacket TODO 想办法合并invocation/request
     * @return
     * @throws Exception
     */
    void sendRequest(URL url, Invocation invocation, SerializeServiceRequestPacket serviceRequestPacket) throws Exception{
        byte[] traceID = invocation.getTraceID();
        Service service = invocation.getService();
        Endpoint endpoint = invocation.getEndpoint();

        long start = TimeUtil.currentTimeMillis();
        long borrowed = start;

        BackendConnectionPool nioConnPool = null;
        BackendConnection conn = null;
        try {
            //获取连接 TODO 地址变化情况
            nioConnPool = getNioConnPool(url,null);
            conn = nioConnPool.borrowObject();
            borrowed = TimeUtil.currentTimeMillis();
            ByteBuffer buffer = serviceRequestPacket.toByteBuffer();
            VenusContext.set(VenusContext.CLIENT_OUTPUT_SIZE,Integer.valueOf(buffer.limit()));

            //发送请求消息，响应由handler类处理
            conn.write(buffer);
            /* TODO tracer log
            VenusTracerUtil.logRequest(traceID, serviceRequestPacket.apiName, JSON.toJSONString(serviceRequestPacket.parameterMap,JSON_FEATURE));
            */
        } catch (Exception e){
            logger.error("sendRequest error.",e);
            throw e;
        }finally {
            /* TODO logger
            if (performanceLogger.isDebugEnabled()) {
                long end = TimeUtil.currentTimeMillis();
                long time = end - borrowed;
                StringBuffer buffer = new StringBuffer();
                buffer.append("[").append(borrowed - start).append(",").append(time).append("]ms (client-callback) traceID=").append(UUID.toString(traceID)).append(", api=").append(serviceRequestPacket.apiName);

                performanceLogger.debug(buffer.toString());
            }
            */

            //TODO 长连接，心跳处理，确认？
            if (conn != null && nioConnPool != null) {
                nioConnPool.returnObject(conn);
            }
        }
    }



    /**
     * 根据远程配置获取nio连接池 TODO 地址变化对连接的影响；地址未变但连接已断开其影响
     * @return
     * @throws Exception
     * @param url
     */
    public BackendConnectionPool getNioConnPool(URL url,RemoteConfig remoteConfig) throws Exception {
        //若存在，则直接使用，否则新建
        String address = String.format("%s:%s",url.getHost(),String.valueOf(url.getPort()));
        if(nioPoolMap.get(address) != null){
            return nioPoolMap.get(address);
        }

        BackendConnectionPool backendConnectionPool = createNioPool(url,new RemoteConfig());
        nioPoolMap.put(address,backendConnectionPool);
        return backendConnectionPool;
        /*
        String ipAddressList = remoteConfig.getFactory().getIpAddressList();
        if(nioPoolMap.get(ipAddressList) != null){
            return nioPoolMap.get(ipAddressList);
        }

        BackendConnectionPool backendConnectionPool = createNioPool(remoteConfig, realPoolMap);
        nioPoolMap.put(remoteConfig.getFactory().getIpAddressList(),backendConnectionPool);
        return backendConnectionPool;
        */
    }

    /**
     * 创建连接池
     * @param url
     * @param remoteConfig
     * @return
     * @throws Exception
     */
    private BackendConnectionPool createNioPool(URL url,RemoteConfig remoteConfig) throws Exception {
        //初始化连接工厂
        VenusBackendConnectionFactory nioFactory = new VenusBackendConnectionFactory();
        nioFactory.setHost(url.getHost());
        nioFactory.setPort(Integer.valueOf(url.getPort()));
        if (remoteConfig.getAuthenticator() != null) {
            nioFactory.setAuthenticator(remoteConfig.getAuthenticator());
        }
        FactoryConfig factoryConfig = remoteConfig.getFactory();
        if (factoryConfig != null) {
            BeanUtils.copyProperties(nioFactory, factoryConfig);
        }
        nioFactory.setConnector(this.connector);
        nioFactory.setMessageHandler(messageHandler);

        //初始化连接池
        BackendConnectionPool nioPool = new PollingBackendConnectionPool("N-" + url.getHost(), nioFactory, 8);
        PoolConfig poolConfig = remoteConfig.getPool();
        if (poolConfig != null) {
            BeanUtils.copyProperties(nioPool, poolConfig);
        }
        nioPool.init();
        return nioPool;
    }


    /**
     * 设置连接超时时间
     * @param conn
     * @param serviceConfig
     * @param endpoint
     * @throws SocketException
     */
    void setConnectionConfig(AbstractBIOConnection conn, ServiceConfig serviceConfig, Endpoint endpoint) throws SocketException {
        int soTimeout = 0;
        int oldTimeout = 0;

        oldTimeout = conn.getSoTimeout();
        if (serviceConfig != null) {
            EndpointConfig endpointConfig = serviceConfig.getEndpointConfig(endpoint.name());
            if (endpointConfig != null) {
                int eTimeOut = endpointConfig.getTimeWait();
                if (eTimeOut > 0) {
                    soTimeout = eTimeOut;
                }
            } else {
                if (serviceConfig.getTimeWait() > 0) {
                    soTimeout = serviceConfig.getTimeWait();
                } else {
                    if (endpoint.timeWait() > 0) {
                        soTimeout = endpoint.timeWait();
                    }
                }
            }
        } else {
            if (endpoint.timeWait() > 0) {
                soTimeout = endpoint.timeWait();
            }
        }
        if (soTimeout > 0) {
            conn.setSoTimeout(soTimeout);
        }
    }

    /**
     * 获取对应请求的响应结果
     * @param messageId
     * @return
     */
    Result fetchResponse(String messageId){
        AbstractServicePacket response = serviceResponseMap.get(messageId);
        logger.info("serviceResponsePacket:{}.",response);
        if(response == null){
            return null;
        }

        if(response instanceof OKPacket){//无返回值
            return new Result(null);
        }else if(response instanceof ServiceResponsePacket){//有返回值
            ServiceResponsePacket serviceResponsePacket = (ServiceResponsePacket)response;
            return new Result(serviceResponsePacket.result);
        }else if(response instanceof ErrorPacket){//调用出错
            ErrorPacket errorPacket = (ErrorPacket)response;
            Result result = new Result();
            result.setErrorCode(errorPacket.errorCode);
            result.setErrorMessage(errorPacket.message);
            return result;
        }else{
            return null;
        }
        //return serviceResponsePacket;
    }

    /**
     * 设置transactionId
     * @param serviceRequestPacket
     * @param athenaTransactionId
     */
    private void setTransactionId(SerializeServiceRequestPacket serviceRequestPacket, AthenaTransactionId athenaTransactionId) {
        if (athenaTransactionId != null) {
            if (athenaTransactionId.getRootId() != null) {
                serviceRequestPacket.rootId = athenaTransactionId.getRootId().getBytes();
            }

            if (athenaTransactionId.getParentId() != null) {
                serviceRequestPacket.parentId = athenaTransactionId.getParentId().getBytes();
            }

            if (athenaTransactionId.getMessageId() != null) {
                serviceRequestPacket.messageId = athenaTransactionId.getMessageId().getBytes();
            }
        }
    }


    public boolean isEnableAsync() {
        return enableAsync;
    }

    public void setEnableAsync(boolean enableAsync) {
        this.enableAsync = enableAsync;
    }

    public VenusExceptionFactory getVenusExceptionFactory() {
        return venusExceptionFactory;
    }

    public void setVenusExceptionFactory(VenusExceptionFactory venusExceptionFactory) {
        this.venusExceptionFactory = venusExceptionFactory;
    }

    public void setServiceFactory(XmlServiceFactory serviceFactory) {
        this.serviceFactory = serviceFactory;
    }

    public short getSerializeType() {
        return serializeType;
    }

    public void setSerializeType(byte serializeType) {
        this.serializeType = serializeType;
    }

    public InvocationListenerContainer getContainer() {
        return container;
    }

    public void setContainer(InvocationListenerContainer container) {
        this.container = container;
    }

    public RemoteConfig getRemoteConfig() {
        return remoteConfig;
    }

    public void setRemoteConfig(RemoteConfig remoteConfig) {
        this.remoteConfig = remoteConfig;
    }

    public VenusClientInvokerMessageHandler getMessageHandler() {
        return messageHandler;
    }

    public void setMessageHandler(VenusClientInvokerMessageHandler messageHandler) {
        this.messageHandler = messageHandler;
    }

    public ConnectionConnector getConnector() {
        return connector;
    }

    public void setConnector(ConnectionConnector connector) {
        this.connector = connector;
    }

    @Override
    public void destroy() throws RpcException{
        if (connector != null) {
            if (connector.isAlive()) {
                connector.shutdown();
            }
        }
        if (connManager != null) {
            if (connManager.isAlive()) {
                connManager.shutdown();
            }
        }
    }

    public int getAsyncExecutorSize() {
        return asyncExecutorSize;
    }

    public void setAsyncExecutorSize(int asyncExecutorSize) {
        this.asyncExecutorSize = asyncExecutorSize;
    }
}
