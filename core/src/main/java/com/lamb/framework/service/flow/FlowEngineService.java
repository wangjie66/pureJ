package com.lamb.framework.service.flow;

import com.lamb.framework.base.Context;
import com.lamb.framework.base.Framework;
import com.lamb.framework.exception.ServiceRuntimeException;
import com.lamb.framework.service.IService;
import com.lamb.framework.service.OP;
import com.lamb.framework.service.flow.constant.FlowConfigConstants;
import com.lamb.framework.service.flow.helper.FlowConfigParser;
import com.lamb.framework.service.flow.model.Flow;
import com.lamb.framework.service.flow.model.Forward;
import com.lamb.framework.service.flow.model.Step;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * <p>Title :  流程引擎服务类</p>
 * <p>Description : 加载并处理流程服务</p>
 * <p>Date : 2017/3/2 22:16</p>
 *
 * @author : hejie (hjnlxuexi@126.com)
 * @version : 1.0
 */
@Slf4j
@Service
public class FlowEngineService implements IService {
    /**
     * 流程配置解析器
     */
    @Resource
    private FlowConfigParser flowConfigParser;

    /**
     * 执行流程服务
     *
     * @param context 数据总线
     */
    @Override
    @Transactional
    public void execute(Context context) {
        try {
            log.debug("执行流程服务【" + context.getServiceName() + "】，开始...");
            long startTime = System.currentTimeMillis();
            //1、获取流程对象
            Flow flow = flowConfigParser.parseFlowConfig(context);
            //2、执行流程步骤
            Map<String, Step> steps = flow.getSteps();
            if (steps.isEmpty()) return;
            //3、开始节点
            Step start = steps.get(Framework.getProperty("channel.flow.start.index"));
            //4、执行流程
            this.run(start, context, steps);
            long endTime = System.currentTimeMillis();
            log.debug("执行流程服务【" + context.getServiceName() + "】，结束【" + (endTime - startTime) + "毫秒】");
        } catch (Exception e) {
            if (e instanceof ServiceRuntimeException)
                throw (ServiceRuntimeException) e;
            throw new ServiceRuntimeException("4000", this.getClass(), e, context.getServiceName());
        }
    }

    /**
     * 驱动执行流程
     *
     * @param step    当前节点
     * @param context 数据总线
     */
    private void run(Step step, Context context, Map<String, Step> steps) {
        String opName;
        //0、判断原子服务是否为数据库原子服务、或者外部原子服务；如：dataBaseOP:testDao/findAllDept
        String ref = step.getRef();
        String[] refConf = ref.trim().split(FlowConfigConstants.OP_SEPARATOR);
        if (refConf.length == 2) {
            opName = refConf[0];//dataBaseOP
            String serviceId = refConf[1];//服务ID：testDao/findAllDept
            context.setServiceId(serviceId);
        } else if (refConf.length == 1) {
            opName = refConf[0];
        } else {
            throw new ServiceRuntimeException("2006", this.getClass(), step.getDesc());
        }
        //1、执行当前节点服务
        Object obj = Framework.getBean(opName);
        if (obj == null) throw new ServiceRuntimeException("2005", this.getClass());
        OP op = (OP) obj;
        op.execute(context);

        //2、判断下一执行节点
        List<Forward> forwards = step.getMapping();
        for (Forward forward : forwards) {
            //是否满足转向条件
            if (!isForward(forward, context)) continue;
            //执行下一个节点
            Step nextStep = steps.get(forward.getTo());
            //检查回环调用
            checkLoopbackCall(step,nextStep,context);
            nextStep.setPreviousStep(step);
            run(nextStep, context, steps);
            //结束流程
            return;
        }
        //3、判断流程是否结束
        String next = step.getNext();
        if (next == null) return;

        //4、执行下一步骤
        Step nextStep = steps.get(next);
        //检查回环调用
        checkLoopbackCall(step,nextStep,context);
        nextStep.setPreviousStep(step);
        run(nextStep, context, steps);
    }

    /**
     * 判断本次转向条件是否成立
     * 条件表达式只支持：
     * 1、布尔值的与、或、非
     * 2、字符串的equals
     * 如：@param1 and @param2
     * 或 @param1 or @param2
     * "db" eq @param2
     * "db" uneq @param2
     * !@param1
     *
     * @param forward 转向实例
     * @param context 数据总线
     * @return 是否转向
     */
    private boolean isForward(Forward forward, Context context) {
        String condition = forward.getCondition();
        String[] meta = condition.trim().split(" ");
        int length = meta.length;
        switch (length) {
            //处理单布尔值表达式 !@param 或者 @param
            case 1:
                return this.getSingleParamExpress(condition, context);
            //处理两个值的条件判断
            case 3:
                return this.getTwoParamsExpress(meta, context);
            //不合法的表达式
            default://不支持的条件表达式
                throw new ServiceRuntimeException("2001", this.getClass(), condition);
        }
    }

    /**
     * 获取两个值表达式的判断结果
     *
     * @param meta    表达式元素数组
     * @param context 数据总线
     * @return 布尔值
     */
    private boolean getTwoParamsExpress(String[] meta, Context context) {
        String left = meta[0];//左表达式
        String operator = meta[1]; //操作符
        String right = meta[2];//右表达式

        //两个字符串比较:相等
        if (operator.equals(FlowConfigConstants.OPERATOR_EQ)) {
            return isEqual(context, left, right);
        }
        //两个字符串比较:不相等
        if (operator.equals(FlowConfigConstants.OPERATOR_UN_EQ)) {
            return !isEqual(context, left, right);
        }
        //两个布尔值比较
        Boolean var1 = getSingleParamExpress(left, context);
        Boolean var2 = getSingleParamExpress(right, context);
        //if (var1 == null || var2 == null) return false;
        //与
        if (operator.equals(FlowConfigConstants.OPERATOR_AND)) return var1 && var2;
        //或
        return operator.equals(FlowConfigConstants.OPERATOR_OR) && (var1 || var2);

    }

    private boolean isEqual(Context context, String left, String right) {
        String var1 = this.getStringVar(left, context);
        String var2 = this.getStringVar(right, context);
        return (var1 == null && var2 == null) || (var1 != null && var2 != null && var1.equals(var2));
    }


    /**
     * 获取单布尔表达式的值
     *
     * @param condition 表达式
     * @param context   数据总线
     * @return 布尔值
     */
    private Boolean getSingleParamExpress(String condition, Context context) {
        char firstChar = condition.charAt(0);
        switch (firstChar) {
            //非 操作 !@param
            case FlowConfigConstants.OPERATOR_NOT:
                //判断变量定义合法性
                String var = condition.substring(1);//@param
                //获取布尔变量的值
                return this.getBooleanVar(var, context);
            //直接布尔值 @param
            case FlowConfigConstants.OPERATOR_AT:
                //获取布尔变量的值
                return this.getBooleanVar(condition, context);
            default://变量表达式不正确
                throw new ServiceRuntimeException("2001", this.getClass(), condition);
        }
    }

    /**
     * 获取布尔变量的值
     *
     * @param var     变量标记
     * @param context 数据总线
     * @return 布尔值
     */
    private Boolean getBooleanVar(String var, Context context) {
        if (var.length() <= 1 || !Character.valueOf(var.charAt(0)).equals(FlowConfigConstants.OPERATOR_AT))
            throw new ServiceRuntimeException("2002", this.getClass(), var);//变量标记不正确
        //判断变量值合法性
        String key = var.substring(1);
        Object value = context.getParams().get(key);
        if (value == null) return false;
        return (value instanceof Boolean) ? (Boolean) value : Boolean.valueOf(value.toString());
    }

    /**
     * 获取字符串变量的值
     *
     * @param var     变量标记
     * @param context 数据总线
     * @return 字符串
     */
    private String getStringVar(String var, Context context) {
        //1、为字符串常量
        if (var.charAt(0) != FlowConfigConstants.OPERATOR_AT)
            return var.equals("null") ? null : var;
        //2、为变量
        Object value = context.getParams().get(var.substring(1));
        if (value == null) return null;

        return value.toString();
    }

    /**
     * 检查回环调用
     *
     * @param current 当前节点
     * @param next    下一节点
     * @param context  总线
     */
    private void checkLoopbackCall(Step current, Step next, Context context) {
        if (
            //1、起始节点被再次调用时，则产生回环
            next.getIndex().equals(Framework.getProperty("channel.flow.start.index")) ||
            //2、下一节点之前被调用过，则产生回环
            next.getPreviousStep() != null
        ) {
            if (
                //3、两个节点来回调用
                (next.getNext()!=null && next.getNext().equals(current.getIndex())) ||
                //4、一个节点自调用
                next.getIndex().equals(current.getIndex())
            ){
                throw new ServiceRuntimeException("2000", this.getClass());
            }
            log.error("【流程服务】" + context.getServiceName() + "，产生回环调用!!!");
        }
    }
}
