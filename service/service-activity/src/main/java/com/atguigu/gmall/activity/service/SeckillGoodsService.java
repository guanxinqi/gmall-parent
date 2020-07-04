package com.atguigu.gmall.activity.service;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.activity.SeckillGoods;

import java.util.List;

/**
 * 服务层接口
 * @author Administrator
 *
 */
public interface SeckillGoodsService {

   /**
    * 返回秒杀商品列表
    * @return
    */
   List<SeckillGoods> findAll();
   

   /**
    * 根据ID获取实体
    * @param id
    * @return
    */
   SeckillGoods getSeckillGoods(Long id);

   //预下单
    void seckillOrder(Long skuId, String userId);

    /***
     * 根据商品id与用户ID查看订单信息
     * @param skuId
     * @param userId
     * @return
     */
    Result checkOrder(Long skuId, String userId);
}