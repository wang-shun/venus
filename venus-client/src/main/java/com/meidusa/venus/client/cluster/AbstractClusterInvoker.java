package com.meidusa.venus.client.cluster;

import com.meidusa.venus.*;
import com.meidusa.venus.client.ClientInvocation;
import com.meidusa.venus.exception.RpcException;
import com.meidusa.venus.client.cluster.loadbalance.Loadbalance;
import com.meidusa.venus.client.cluster.loadbalance.RandomLoadbalance;
import com.meidusa.venus.client.cluster.loadbalance.RoundLoadbalance;
import com.meidusa.venus.support.VenusConstants;
import com.meidusa.venus.util.VenusLoggerFactory;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 集群容错调用抽象类，将集群容错透明化
 * Created by Zhangzhihua on 2017/9/13.
 */
public abstract class AbstractClusterInvoker implements ClusterInvoker {

    private static Logger logger = VenusLoggerFactory.getDefaultLogger();

    private RandomLoadbalance randomLoadbanlance = new RandomLoadbalance();

    private RoundLoadbalance roundLoadbanlance = new RoundLoadbalance();

    //服务路径-randomlb映射表
    private static Map<String,RandomLoadbalance> randomLbMap = new ConcurrentHashMap<String,RandomLoadbalance>();

    //服务路径-roundlb映射表
    private static Map<String,RoundLoadbalance> roundLbMap = new ConcurrentHashMap<String,RoundLoadbalance>();

    protected Invoker invoker = null;

    public Invoker getInvoker() {
        return invoker;
    }

    public void setInvoker(Invoker invoker) {
        this.invoker = invoker;
    }

    /**
     * 调用，将网络连接异常透明化，对连接不可用场景默认进行重试操作
     * @param invocation
     * @param urlList
     * @return
     * @throws RpcException
     */
    Result doInvokeForNetworkFailover(Invocation invocation, List<URL> urlList) throws RpcException {
        ClientInvocation clientInvocation = (ClientInvocation)invocation;
        int networkFailRetries = 3;
        for(int i=0;i<networkFailRetries;i++){
            try {
                //选择地址
                URL url = getLoadbanlance(clientInvocation.getLoadbalance(),clientInvocation).select(urlList);
                if(logger.isDebugEnabled()){
                    logger.debug("select service provider:【{}】.",new StringBuilder().append(url.getHost()).append(":").append(url.getPort()));
                }

                // 调用
                return  getInvoker().invoke(invocation, url);
            } catch (RpcException e) {
                //对于网络socket异常，如获取连接池、连接无效等
                if(e.isNetwork()){
                    if(i < networkFailRetries){
                    }else{
                        throw e;
                    }
                }else{
                    throw e;
                }
            } catch (Throwable t){
                throw t;
            }
        }

        throw new RpcException(String.format("invoke method:%s/%s failed,cannot get connection.",invocation.getServiceName(),invocation.getMethodName()));
    }

    /**
     * 获取loadbanlance
     * @return
     */
    Loadbalance getLoadbanlance(String lb, ClientInvocation clientInvocation){
        //目前，选择lb到服务级别
        String servicePath = clientInvocation.getServicePath();
        if(VenusConstants.LOADBALANCE_RANDOM.equals(lb)){
            if(randomLbMap.get(servicePath) == null){
                randomLbMap.put(servicePath,randomLoadbanlance);
            }
            return randomLbMap.get(servicePath);
        }else if(VenusConstants.LOADBALANCE_ROUND.equals(lb)){
            if(roundLbMap.get(servicePath) == null){
                roundLbMap.put(servicePath,roundLoadbanlance);
            }
            return roundLbMap.get(servicePath);
        }else{
            throw new RpcException("unspport loadbanlance policy.");
        }
    }
}
