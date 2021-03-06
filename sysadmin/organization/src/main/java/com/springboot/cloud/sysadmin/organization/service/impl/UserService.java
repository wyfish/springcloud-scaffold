package com.springboot.cloud.sysadmin.organization.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.springboot.cloud.sysadmin.organization.dao.UserMapper;
import com.springboot.cloud.sysadmin.organization.entity.param.UserQueryParam;
import com.springboot.cloud.sysadmin.organization.entity.po.User;
import com.springboot.cloud.sysadmin.organization.entity.vo.UserVo;
import com.springboot.cloud.sysadmin.organization.service.IUserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class UserService implements IUserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserRoleService userRoleService;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Override
    @Transactional
    public long add(User user) {
        user.setPassword(passwordEncoder().encode(user.getPassword()));
        long inserts = userMapper.insert(user);
        userRoleService.saveBatch(user.getId(), user.getRoleIds());
        return inserts;
    }

    @Override
    @Transactional
    @CacheEvict(value = "user", key = "#root.targetClass.name+'-'+#id")
    public void delete(long id) {
        userMapper.deleteById(id);
        userRoleService.removeByUserId(id);
    }

    @Override
    @Transactional
    @CacheEvict(value = "user", key = "#root.targetClass.name+'-'+#user.id")
    public void update(User user) {
        userMapper.updateById(user);
        userRoleService.saveOrUpdateBatch(user.getId(), user.getRoleIds());
    }

    @Override
    @Cacheable(value = "user", key = "#root.targetClass.name+'-'+#id")
    public User get(long id) {
        User user = userMapper.selectById(id);
        user.setRoleIds(userRoleService.queryByUserId(id));
        return user;
    }

    @Override
    public User getByUniqueId(String uniqueId) {
        User user = userMapper.selectOne(new QueryWrapper<User>()
                .eq("username", uniqueId)
                .or()
                .eq("mobile", uniqueId));
        user.setRoleIds(userRoleService.queryByUserId(user.getId()));
        return user;
    }

    @Override
    public IPage<UserVo> query(Page page, UserQueryParam userQueryParam) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.ge(null != userQueryParam.getCreatedTimeStart(), "created_time", userQueryParam.getCreatedTimeStart());
        queryWrapper.le(null != userQueryParam.getCreatedTimeEnd(), "created_time", userQueryParam.getCreatedTimeEnd());
        queryWrapper.eq(StringUtils.isNotBlank(userQueryParam.getName()), "name", userQueryParam.getName());
        queryWrapper.eq(StringUtils.isNotBlank(userQueryParam.getUsername()), "username", userQueryParam.getUsername());
        queryWrapper.eq(StringUtils.isNotBlank(userQueryParam.getMobile()), "mobile", userQueryParam.getMobile());
        // 转换成VO
        IPage<UserVo> iPage = userMapper.selectPage(page, queryWrapper).convert((user) -> {
            UserVo userVo = new UserVo();
            BeanUtils.copyProperties(user, userVo);
            return userVo;
        });
        return iPage;
    }
}
