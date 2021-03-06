package com.meidusa.venus.client.router.condition.determin;

import com.meidusa.venus.client.router.condition.ConditionRuleConstants;
import com.meidusa.venus.client.router.condition.rule.FullConditionRule;

/**
 * App语义规则判断逻辑
 * Created by Zhangzhihua on 2017/8/29.
 */
public class AppRuleDetermin {

    /**
     *
     * @param appExp 表达式类型，等于(= EQ)或不等于(!= NEQ)
     * @param sourceApp 源APP名称
     * @param targetApps 目标匹配APP名称
     * @return
     */
    public static boolean isReject(String appExp,String sourceApp,String targetApps){
        appExp = appExp.trim();
        if(appExp.equalsIgnoreCase(ConditionRuleConstants.EQ)){//EQ/白名单
            //允许APP名单
            String allowApps = targetApps;
            return !allowApps.contains(sourceApp);
        }else if(appExp.equalsIgnoreCase(ConditionRuleConstants.NEQ)){//NEQ/黑名单
            //拒绝APP名单
            String refuseApps = targetApps;
            return refuseApps.contains(sourceApp);
        }
        return false;
    }

}
