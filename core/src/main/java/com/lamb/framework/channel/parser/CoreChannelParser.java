package com.lamb.framework.channel.parser;

import com.lamb.framework.base.Context;
import com.lamb.framework.channel.constant.ServiceConfConstants;
import com.lamb.framework.channel.constant.ServicePacketConstants;
import com.lamb.framework.channel.helper.ServiceConfigParser;
import com.lamb.framework.exception.ServiceRuntimeException;
import com.lamb.framework.validator.ConfigValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>Title : 服务请求报文解析器</p>
 * <p>Description : 解析请求报文</p>
 * <p>Date : 2017/3/3 16:00</p>
 *
 * @author : hejie (hjnlxuexi@126.com)
 * @version : 1.0
 */
@Slf4j
@Component
public class CoreChannelParser implements ICoreChannelParser {
    /**
     * 服务配置解析器
     */
    @Resource
    private ServiceConfigParser serviceConfigParser;
    /**
     * 解析请求报文
     * @param context 数据总线
     */
    @Override
    public void parse(Context context){
        log.debug("解析服务请求报文，开始...");
        long start = System.currentTimeMillis();
        //0、处理原始报文
        this.dealPacket(context);
        //1、获取服务配置对象
        Map config = serviceConfigParser.parseServiceConf(context);
        //2、将服务基本信息放入总线
        this.setServiceAttr(config, context);
        //3、根据服务配置处理请求数据
        this.dealReqData(config, context);
        long end = System.currentTimeMillis();
        log.debug("解析服务请求报文，结束【"+(end-start)+"毫秒】");
    }

    /**
     * 处理原始报文
     * @param context 数据总线
     */
    private void dealPacket(Context context){
        //0、原始报文
        Map packet  = context.getServiceInput();
        if (packet==null)//请求报文为空
            throw new ServiceRuntimeException("1000" , this.getClass());
        //1、获取报文头
        Object _header = packet.get(ServicePacketConstants.HEADER);
        if (_header==null) //请求报文头为空
            throw new ServiceRuntimeException("1001" , this.getClass());
        Map header = (Map)_header;
        //2、获取服务名
        Object _service = header.get(ServicePacketConstants.SERVICE);
        if (_service==null) //请求报文头服务编码为空
            throw new ServiceRuntimeException("1002" , this.getClass());
        String service = (String)_service;
        //3、存放服务编码
        context.setServiceCode(service);
    }

    /**
     * 设置服务属性到数据总线
     * @param config 服务配置
     * @param context 数据总线
     */
    private void setServiceAttr(Map config, Context context){
        //0、服务名称
        if (config.get(ServiceConfConstants.NAME_TAG)==null) //服务配置中必须包含服务名称
            throw new ServiceRuntimeException("1005" , this.getClass());
        String serviceName = config.get(ServiceConfConstants.NAME_TAG).toString();
        context.setServiceName(serviceName);
        //1、服务ID
        if (config.get(ServiceConfConstants.ID_TAG)==null) //服务配置中必须包含服务ID
            throw new ServiceRuntimeException("1005" , this.getClass());
        String serviceID = config.get(ServiceConfConstants.ID_TAG).toString();
        //3、过路交易类型
        if (config.get(ServiceConfConstants.TYPE_TAG)!=null ){
            String type = config.get(ServiceConfConstants.TYPE_TAG).toString();
            context.setType(type);
        }
        context.setServiceId(serviceID);
    }

    /**
     * 处理请求数据
     * 1、数据校验
     * 2、数据筛选
     * @param config 服务配置对象
     * @param context 数据总线
     */
    @SuppressWarnings("unchecked")
    private void dealReqData(Map config, Context context){
        List<Map> inputList = (List<Map>) config.get(ServiceConfConstants.INPUT_TAG);
        //1、请求报文
        Map packet = context.getServiceInput();
        Object _body = packet.get(ServicePacketConstants.BODY);
        if (_body==null) _body = new HashMap();
        Map params = (Map)_body;
        //2、遍历输入域列表
        for (Map field : inputList) {
            if (field.get(ServiceConfConstants.NAME_PROP)==null) //服务配置中必须存在字段名称
                throw new ServiceRuntimeException("1005" , this.getClass());
            String name = field.get(ServiceConfConstants.NAME_PROP).toString();
            Object value = params.get(name);
            //3、验证字段值
            value = ConfigValidator.validateField(value,field);
            //4、将字段键值对放入总线

            Object target_name = field.get(ServiceConfConstants.TARGET_NAME_PROP);
            if (target_name!=null&&!target_name.toString().isEmpty()) //name --> targetName
                name = target_name.toString();
            context.getParams().put(name,value);
        }
    }
}
