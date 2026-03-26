package com.miu.codemain.service.impl;

import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.miu.codemain.model.entity.App;
import com.miu.codemain.mapper.AppMapper;
import com.miu.codemain.service.AppService;
import org.springframework.stereotype.Service;

/**
 * 应用 服务层实现。
 *
 * @author <a href="https://github.com/YeXingKe">野行客</a>
 */
@Service
public class AppServiceImpl extends ServiceImpl<AppMapper, App>  implements AppService{

}
