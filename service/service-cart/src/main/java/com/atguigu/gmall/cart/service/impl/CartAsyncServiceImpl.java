package com.atguigu.gmall.cart.service.impl;

import com.atguigu.gmall.cart.mapper.CartInfoMapper;
import com.atguigu.gmall.cart.service.CartAsyncService;
import com.atguigu.gmall.model.cart.CartInfo;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class CartAsyncServiceImpl implements CartAsyncService {

    @Autowired
    private CartInfoMapper cartInfoMapper;


    @Async
    @Override
    public void updateCartInfo(CartInfo cartInfo) {
        log.info("方法开始执行--updateCartInfo-{}" + Thread.currentThread().getName());
        cartInfoMapper.updateById(cartInfo);
    }

    @Async
    @Override
    public void saveCartInfo(CartInfo cartInfo) {
        log.info("方法开始执行--saveCartInfo-{}" + Thread.currentThread().getName());
        cartInfoMapper.insert(cartInfo);
    }


    @Async
    @Override
    public void deleteCartInfo(String userId) {
        cartInfoMapper.delete(new QueryWrapper<CartInfo>().eq("user_id", userId));
    }


    @Async
    @Override
    public void checkCart(String userId, Integer isChecked, Long skuId) {
        CartInfo cartInfo = new CartInfo();
        cartInfo.setIsChecked(isChecked);
        QueryWrapper queryWrapper = new QueryWrapper<CartInfo>();
        queryWrapper.eq("user_id", userId);
        queryWrapper.eq("sku_id", skuId);
        cartInfoMapper.update(cartInfo, queryWrapper);
    }



    @Async
    @Override
    public void deleteCartInfo(String userId, Long skuId) {
        cartInfoMapper.delete(new QueryWrapper<CartInfo>().eq("user_id", userId).eq("sku_id", skuId));
    }

}
