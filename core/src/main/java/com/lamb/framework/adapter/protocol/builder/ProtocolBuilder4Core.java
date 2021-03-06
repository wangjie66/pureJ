package com.lamb.framework.adapter.protocol.builder;

import com.lamb.framework.adapter.protocol.constant.AdapterConfConstants;
import com.lamb.framework.base.Context;
import com.lamb.framework.exception.ServiceRuntimeException;
import com.lamb.framework.validator.ConfigValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>Title : 框架服务报文组装器</p>
 * <p>Description : 组装本框架服务报文</p>
 * <p>Date : 2017/4/16 11:25</p>
 *
 * @author : hejie (hjnlxuexi@126.com)
 * @version : 1.0
 */
@Slf4j
@Component
public class ProtocolBuilder4Core implements IProtocolBuilder {
    /**
     * 组装报文
     * @param context 数据总线
     * @param adapterConfig 适配器配置
     */
    @Override
    public void build(Context context  , Map adapterConfig) {
        log.debug("组装外部服务【"+adapterConfig.get(AdapterConfConstants.NAME_TAG)+"】请求报文，开始...");
        long start = System.currentTimeMillis();
        //0、请求报文
        Map data = new HashMap();
        //1、组装报文头
        this.buildHeader(adapterConfig , data);
        //2、组装报文体
        this.buildBody(context , adapterConfig, data);
        //3、请求报文放入总线
        context.setRequestData(data);
        long end = System.currentTimeMillis();
        log.debug("组装外部服务服务【"+adapterConfig.get(AdapterConfConstants.NAME_TAG)+"】请求报文，结束【"+(end-start)+"毫秒】");
    }

    /**
     * 组装报文头
     * @param adapterConfig 适配器配置对象
     */
    @SuppressWarnings("unchecked")
    private void buildHeader(Map adapterConfig , Map data){
        Map header = new HashMap();
        Object _service = adapterConfig.get(AdapterConfConstants.SERVICE_TAG);
        if (_service==null)//服务配置结构不正确
            throw new ServiceRuntimeException("5001" , this.getClass());
        String service = _service.toString();
        header.put("service" , service);
        //放入到总线外部请求报文区域
        data.put("header" , header);
    }

    /**
     * 组装报文体
     * @param context 数据总线
     * @param adapterConfig 适配器配置对象
     */
    @SuppressWarnings("unchecked")
    private void buildBody(Context context  , Map adapterConfig , Map data){
        Map body = new HashMap();
        Map params = context.getParams();
        Object inputObj = adapterConfig.get(AdapterConfConstants.INPUT_TAG);
        if (inputObj==null)//服务配置结构不正确
            throw new ServiceRuntimeException("5001" , this.getClass());
        List<Map> inputList = (List<Map>)inputObj;
        //3、遍历输入域列表
        for (Map field : inputList) {
            Object _name = field.get(AdapterConfConstants.NAME_PROP);
            if ( _name== null) //服务配置中必须存在字段名称
                throw new ServiceRuntimeException("5001" , this.getClass());
            String name = _name.toString();
            Object value = params.get(name);
            //4、验证字段值
            value = ConfigValidator.validateField(value, field);
            //5、字段名转换
            Object target_name = field.get(AdapterConfConstants.TARGET_NAME_PROP);
            if (target_name!=null&&!target_name.toString().isEmpty()) name = target_name.toString();
            //6、将字段键值对放入总线
            body.put(name, value);
        }
        data.put("body", body);
    }
}
