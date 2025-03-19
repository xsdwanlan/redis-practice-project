package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;

import javax.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IUserService extends IService<User> {
    //session登录——发送短信
    Result sendcode(String phone, HttpSession session);

    //session登录——登录
    Result login(LoginFormDTO loginForm, HttpSession session);

    //签到
    Result sign();
    //签到天数
    Result signCount();
}
