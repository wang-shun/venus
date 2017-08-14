package com.meidusa.venus;


/**
 * filte
 * Created by Zhangzhihua on 2017/8/1.
 */
public interface Filter {

    /**
     * 横切面接口
     * @param invocation
     * @return 返回值，如降级处理，若被降级需要返回自定义值则返回值不为空；
     * 若返回值为空，则表示不需要返回继续后续流程
     * @throws RpcException
     */
    Result invoke(Invocation invocation) throws RpcException;
}