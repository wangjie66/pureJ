package com;

import com.lamb.framework.base.Framework;
import com.lamb.framework.listener.AbstractListener;
import com.lamb.framework.util.BizConfigHotLoading;
import com.lamb.framework.util.MybatisMapperHotLoading;
import com.lamb.framework.util.PropertySourceHotLoading;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

import java.util.Comparator;
import java.util.List;

/**
 * <p>Title : 应用启动入口</p>
 * <p>Description : 通过springBoot管理启动的应用入口</p>
 * <p>Date : 2017/2/28 21:03</p>
 *
 * @author : hejie (hjnlxuexi@126.com)
 * @version : 1.0
 */
@Slf4j
@SpringBootApplication
public class Application  {
    /**
     * 以java引用形式启动
     * @param args 参数
     */
    public static void main(String[] args) {
        ApplicationContext applicationContext = SpringApplication.run(Application.class , args);
        Framework.setSpringCtx(applicationContext);
        //设置监听器
        setListeners(applicationContext);
        //启动热加载
        hotLoading(applicationContext);
        log.info("系统启动成功！！！");
    }

    /**
     * 热加载
     * 1、mapper文件热加载
     * 2、系统配置热加载
     */
    private static void hotLoading(ApplicationContext applicationContext){
        Environment environment = applicationContext.getEnvironment();
        //启动 mapper文件热加载
        if ( Boolean.valueOf(environment.getProperty("jdbc.mapper.hotLoading")) ) MybatisMapperHotLoading.init(2 , 0 , 30);
        //启动 系统配置文件热加载
        if ( Boolean.valueOf(environment.getProperty("server.config.hotLoading")) ) PropertySourceHotLoading.init(2 , 0 , 30);
        //启动 业务配置热加载
        if ( Boolean.valueOf(environment.getProperty("biz.hotLoading")) ) BizConfigHotLoading.init(5 , 0 , 10);
    }

    /**
     * 设置所有监听器
     */
    @SuppressWarnings("unchecked")
    private static void setListeners(ApplicationContext springCtx){
        //1、获取监听器列表
        List<AbstractListener> list = (List<AbstractListener>)springCtx.getBean("listeners");
        //2、清空列表
        list.clear();
        //3、自动扫描，并添加所有监听器
        String[] listeners = springCtx.getBeanNamesForType(AbstractListener.class);
        for (String listenerName : listeners) {
            AbstractListener listener = (AbstractListener) springCtx.getBean(listenerName);
            list.add(listener);
        }
        //4、排序
        list.sort(Comparator.comparing(AbstractListener::getSort));
    }

}
