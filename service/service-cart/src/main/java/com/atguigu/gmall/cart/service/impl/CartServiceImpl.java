package com.atguigu.gmall.cart.service.impl;

import com.atguigu.gmall.cart.mapper.CartInfoMapper;
import com.atguigu.gmall.cart.service.CartAsyncService;
import com.atguigu.gmall.cart.service.CartService;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.model.cart.CartInfo;
import com.atguigu.gmall.model.product.SkuInfo;
import com.atguigu.gmall.product.client.ProductFeignClient;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class CartServiceImpl implements CartService {
    @Autowired
    private CartInfoMapper cartInfoMapper;
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private ProductFeignClient productFeignClient;

    @Autowired
    private CartAsyncService cartAsyncService;

    @Override
    public void addToCart(Long skuId, String userId, Integer skuNum) {
        // 获取购物车的key
        String cartKey = getCartKey(userId);

        // 获取数据库对象
        QueryWrapper<CartInfo> cartInfoQueryWrapper = new QueryWrapper<>();
        cartInfoQueryWrapper.eq("sku_id",skuId).eq("user_id",userId);
        CartInfo cartInfoExist = cartInfoMapper.selectOne(cartInfoQueryWrapper);

        // 说明缓存中有数据
        if (cartInfoExist!=null){
            // 数量相加
            cartInfoExist.setSkuNum(cartInfoExist.getSkuNum()+skuNum);
            // 查询最新价格
            BigDecimal skuPrice = productFeignClient.getSkuPrice(skuId);
            cartInfoExist.setSkuPrice(skuPrice);
            // 更新数据
//            cartInfoMapper.updateById(cartInfoExist);
            cartAsyncService.updateCartInfo(cartInfoExist);
        } else {
            CartInfo cartInfo1 = new CartInfo();
            SkuInfo skuInfo = productFeignClient.getSkuInfoById(skuId);
            cartInfo1.setSkuPrice(skuInfo.getPrice());
            cartInfo1.setCartPrice(skuInfo.getPrice());
            cartInfo1.setSkuNum(skuNum);
            cartInfo1.setSkuId(skuId);
            cartInfo1.setUserId(userId);
            cartInfo1.setImgUrl(skuInfo.getSkuDefaultImg());
            cartInfo1.setSkuName(skuInfo.getSkuName());
            // 新增数据
//            cartInfoMapper.insert(cartInfo1);
            cartAsyncService.saveCartInfo(cartInfo1);
            cartInfoExist = cartInfo1;
        }
        // 更新缓存
        // hset(key,field,value)
        redisTemplate.boundHashOps(cartKey).put(skuId.toString(),cartInfoExist);
//        redisTemplate.opsForHash().put(cartKey,skuId.toString(),cartInfoExist);
        // 设置过期时间
        setCartKeyExpire(cartKey);
    }
    // 设置过期时间
    private void setCartKeyExpire(String cartKey) {
        redisTemplate.expire(cartKey, RedisConst.USER_CART_EXPIRE, TimeUnit.SECONDS);
    }

    // 获取购物车的key
    private String getCartKey(String userId) {
        //定义key user:userId:cart
        return RedisConst.USER_KEY_PREFIX + userId +       RedisConst.USER_CART_KEY_SUFFIX;
    }


    @Override
    public List<CartInfo> getCartList(String userId, String userTempId) {
        // 什么一个返回的集合对象
        List<CartInfo> cartInfoList = new ArrayList<>();

        // 未登录：临时用户Id 获取未登录的购物车数据
        if (StringUtils.isEmpty(userId)) {
            cartInfoList = this.getCartList(userTempId);
            return cartInfoList;
        }

        /*
         1. 准备合并购物车
         2. 获取未登录的购物车数据
         3. 如果未登录购物车中有数据，则进行合并 合并的条件：skuId 相同 则数量相加，合并完成之后，删除未登录的数据！
         4. 如果未登录购物车没有数据，则直接显示已登录的数据
          */
        //已登录
        if (!StringUtils.isEmpty(userId)) {
            List<CartInfo> cartInfoArrayList = this.getCartList(userTempId);
            if (!CollectionUtils.isEmpty(cartInfoArrayList)) {
                // 如果未登录购物车中有数据，则进行合并 合并的条件：skuId 相同
                cartInfoList = this.mergeToCartList(cartInfoArrayList, userId);
                // 删除未登录购物车数据
                this.deleteCartList(userTempId);
            }

            // 如果未登录购物车中没用数据！
            if (StringUtils.isEmpty(userTempId) || CollectionUtils.isEmpty(cartInfoArrayList)) {
                // 根据什么查询？userId
                cartInfoList = this.getCartList(userId);
            }
        }
        return cartInfoList;
    }

    private void deleteCartList(String userTempId) {
        // 删除数据库，删除缓存
        // delete from userInfo where userId = ?userTempId
        QueryWrapper queryWrapper = new QueryWrapper<CartInfo>();
        queryWrapper.eq("user_id", userTempId);
//        cartInfoMapper.delete(queryWrapper);
        cartAsyncService.deleteCartInfo(userTempId);


        String cartKey = getCartKey(userTempId);
        Boolean flag = redisTemplate.hasKey(cartKey);
        if (flag){
            redisTemplate.delete(cartKey);
        }
    }
    /**
     * 合并
     * @param cartInfoArrayList
     * @param userId
     * @return
     */
    private List<CartInfo> mergeToCartList(List<CartInfo> cartInfoArrayList, String userId) {
         /*
    demo1:
        登录：
            37 1
            38 1
        未登录：
            37 1
            38 1
            39 1
        合并之后的数据
            37 2
            38 2
            39 1
     demo2:
         未登录：
            37 1
            38 1
            39 1
            40  1
          合并之后的数据
            37 1
            38 1
            39 1
            40  1
     */
        //已登录购物车
        List<CartInfo> cartInfoListLogin = this.getCartList(userId);
        Map<Long, CartInfo> cartInfoMapLogin = cartInfoListLogin.stream().collect(Collectors.toMap(CartInfo::getSkuId, cartInfo -> cartInfo));

        for (CartInfo cartInfoNoLogin : cartInfoArrayList) {
            Long skuId = cartInfoNoLogin.getSkuId();
            // 有更新数量
            if (cartInfoMapLogin.containsKey(skuId)) {
                CartInfo cartInfoLogin = cartInfoMapLogin.get(skuId);
                // 数量相加
                cartInfoLogin.setSkuNum(cartInfoLogin.getSkuNum() + cartInfoNoLogin.getSkuNum());

                //合并数据：勾选
                // 未登录状态选中的商品！
                if (cartInfoNoLogin.getIsChecked().intValue() == 1) {
                    cartInfoLogin.setIsChecked(1);
                }

                // 更新数据库
                //cartInfoMapper.updateById(cartInfoLogin);
                cartAsyncService.updateCartInfo(cartInfoLogin);
            } else {
                cartInfoNoLogin.setUserId(userId);
                //cartInfoMapper.insert(cartInfoNoLogin);
                cartAsyncService.saveCartInfo(cartInfoNoLogin);
            }
        }
        // 汇总数据 37 38 39
        List<CartInfo> cartInfoList = loadCartCache(userId); // 数据库中的数据
        return cartInfoList;
    }

    /**
     * 根据用户获取购物车
     * @param userId
     * @return
     */
    private List<CartInfo> getCartList(String userId) {
        // 声明一个返回的集合对象
        List<CartInfo> cartInfoList = new ArrayList<>();
        if (StringUtils.isEmpty(userId)) return cartInfoList;

    /*
    1.  根据用户Id 查询 {先查询缓存，缓存没有，再查询数据库}
     */
        // 定义key user:userId:cart
        String cartKey = this.getCartKey(userId);
        // 获取数据
        cartInfoList = redisTemplate.opsForHash().values(cartKey);
        if (!CollectionUtils.isEmpty(cartInfoList)) {
            // 购物车列表显示有顺序：按照商品的更新时间 降序
            cartInfoList.sort(new Comparator<CartInfo>() {
                @Override
                public int compare(CartInfo o1, CartInfo o2) {
                    // str1 = ab str2 = ac;
                    return o1.getId().toString().compareTo(o2.getId().toString());
                }
            });
            return cartInfoList;
        } else {
            // 判断缓存中是否有cartKey，先加载数据库中的数据放入缓存！
            if (!redisTemplate.hasKey(cartKey)) {
                loadCartCache(userId);
            }
//            // 缓存中没用数据！
//            cartInfoList = loadCartCache(userId);
            return cartInfoList;
        }
    }

    /**
     * 通过userId 查询购物车并放入缓存！
     * @param userId
     * @return
     */
    public List<CartInfo> loadCartCache(String userId) {
        QueryWrapper queryWrapper = new QueryWrapper<CartInfo>();
        queryWrapper.eq("user_id", userId);
        List<CartInfo> cartInfoList = cartInfoMapper.selectList(queryWrapper);
        if (CollectionUtils.isEmpty(cartInfoList)) {
            return cartInfoList;
        }
        // 将数据库中的数据查询并放入缓存
        HashMap<String, CartInfo> map = new HashMap<>();
        for (CartInfo cartInfo : cartInfoList) {
            BigDecimal skuPrice = productFeignClient.getSkuPrice(cartInfo.getSkuId());
            cartInfo.setSkuPrice(skuPrice);
            map.put(cartInfo.getSkuId().toString(), cartInfo);
        }

        // 定义key user:userId:cart
        String cartKey = this.getCartKey(userId);
        redisTemplate.opsForHash().putAll(cartKey, map);
        // 设置过期时间
        this.setCartKeyExpire(cartKey);
        return cartInfoList;
    }


    @Override
    public void checkCart(String userId, Integer isChecked, Long skuId) {
        // update cartInfo set isChecked=? where  skuId = ? and userId=？
        // 修改数据库
        // 第一个参数表示修改的数据，第二个参数表示条件
        cartAsyncService.checkCart(userId, isChecked, skuId);

        // 修改缓存
        // 定义key user:userId:cart
        String cartKey = this.getCartKey(userId);
        BoundHashOperations<String, String, CartInfo> hashOperations = redisTemplate.boundHashOps(cartKey);
        // 先获取用户选择的商品
        if (hashOperations.hasKey(skuId.toString())) {
            CartInfo cartInfoUpd = hashOperations.get(skuId.toString());
            // cartInfoUpd 写会缓存
            cartInfoUpd.setIsChecked(isChecked);

            // 更新缓存
            hashOperations.put(skuId.toString(), cartInfoUpd);
            // 设置过期时间
            this.setCartKeyExpire(cartKey);
        }
    }

    //删除
    @Override
    public void deleteCart(Long skuId, String userId) {
        String cartKey = getCartKey(userId);
        cartAsyncService.deleteCartInfo(userId, skuId);

        //获取缓存对象
        BoundHashOperations<String, String, CartInfo> hashOperations = redisTemplate.boundHashOps(cartKey);
        if (hashOperations.hasKey(skuId.toString())){
            hashOperations.delete(skuId.toString());
        }
    }


    @Override
    public List<CartInfo> getCartCheckedList(String userId) {
        List<CartInfo> cartInfoList = new ArrayList<>();

        // 定义key user:userId:cart
        String cartKey = this.getCartKey(userId);
        List<CartInfo> cartCachInfoList = redisTemplate.opsForHash().values(cartKey);
        if (null != cartCachInfoList && cartCachInfoList.size() > 0) {
            for (CartInfo cartInfo : cartCachInfoList) {
                // 获取选中的商品！
                if (cartInfo.getIsChecked().intValue() == 1) {
                    cartInfoList.add(cartInfo);
                }
            }
        }
        return cartInfoList;
    }

}
