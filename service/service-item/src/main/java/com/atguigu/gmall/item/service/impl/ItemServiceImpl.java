package com.atguigu.gmall.item.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.item.service.ItemService;
import com.atguigu.gmall.list.client.ListFeignClient;
import com.atguigu.gmall.model.product.BaseCategoryView;
import com.atguigu.gmall.model.product.SkuInfo;
import com.atguigu.gmall.model.product.SpuSaleAttr;
import com.atguigu.gmall.product.client.ProductFeignClient;
import org.apache.tomcat.util.threads.ThreadPoolExecutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;


/**
 * @author mqx
 * @date 2020/6/13 11:32
 */
@Service
public class ItemServiceImpl implements ItemService {

    @Autowired
    private ProductFeignClient productFeignClient;

    @Autowired
    private ListFeignClient listFeignClient;

    // 编写一个自定义的线程池！
//    @Autowired
//    private ThreadPoolExecutor threadPoolExecutor;

    @Override
    public Map<String, Object> getBySkuId(Long skuId) {
        Map<String, Object> result = new HashMap<>();

        // 异步编排  通过skuId 获取skuInfo 对象数据 ，这个skuInfo 在后面会使用到其中的属性
        CompletableFuture<SkuInfo> skuInfoCompletableFuture = CompletableFuture.supplyAsync(() -> {
            SkuInfo skuInfo = productFeignClient.getSkuInfoById(skuId);
            result.put("skuInfo", skuInfo);
            return skuInfo;
        });
        // 查询销售属性，销售属性值的时候，返回来的集合只需要保存到map中，并没有任何方法，需要这个集合数据作为参数传递。
        // 销售属性值，销售属性值的时候 需要skuInfo对象中的getSpuId 所以此处应该使用skuInfoCompletableFuture！
        // 不使用，supplyAsync runAsync.没有该方法 ，
//        Consumerbase_category_view
//        idea 默认写法 也可以实现！ 复制小括号，写死右箭头，落地大括号
        CompletableFuture<Void> spuSaleAttrCompletableFuture = skuInfoCompletableFuture.thenAcceptAsync((skuInfo -> {
            List<SpuSaleAttr> spuSaleAttrListCheckBySku = productFeignClient.getSpuSaleAttrListCheckBySku(skuId, skuInfo.getSpuId());
            // 保存到map 集合
            result.put("spuSaleAttrList", spuSaleAttrListCheckBySku);
        }));
//        CompletableFuture<Void> spuSaleAttrCompletableFuture = skuInfoCompletableFuture.thenAcceptAsync((skuInfo) -> {
//            List<SpuSaleAttr> spuSaleAttrListCheckBySku = productFeignClient.getSpuSaleAttrListCheckBySku(skuId, skuInfo.getSpuId());
////            // 保存到map 集合
//            result.put("spuSaleAttrList", spuSaleAttrListCheckBySku);
//        },threadPoolExecutor);

        // 查询分类数据，需要skuInfo的三级分类Id
//        Consumer
        CompletableFuture<Void> categoryViewCompletableFuture = skuInfoCompletableFuture.thenAcceptAsync((skuInfo) -> {
            BaseCategoryView categoryView = productFeignClient.getCategoryView(skuInfo.getCategory3Id());
            // 保存三级分类数据
            result.put("categoryView", categoryView);
        });

        // 通过skuId 获取价格数据 runAsync 不需要返回值！
        // 方法一：
        CompletableFuture<Void> priceCompletableFuture = CompletableFuture.runAsync(() -> {
            BigDecimal skuPrice = productFeignClient.getSkuPrice(skuId);
            // 保存商品价格
            result.put("price", skuPrice);
        });
//        方法二
//        skuInfoCompletableFuture.thenAcceptAsync((skuInfo -> {
//            BigDecimal skuPrice = productFeignClient.getSkuPrice(skuInfo.getId());
//            // 保存商品价格
//            result.put("price", skuPrice);
//        }));

        // 根据spuId 获取 由销售属性值Id 和skuId 组成的map 集合数据 ,第二个参数是一个线程池。如果不写，程序会有一个默认的线程池。
        CompletableFuture<Void> valuesSkuJsonCompletableFuture = skuInfoCompletableFuture.thenAcceptAsync((skuInfo -> {
            Map skuValueIdsMap = productFeignClient.getSkuValueIdsMap(skuInfo.getSpuId());
//             需要将skuValueIdsMap 转化为Json 字符串，给页面使用!  Map --->Json
            String valuesSkuJson = JSON.toJSONString(skuValueIdsMap);
            // 保存销售属性值Id 和 skuId 组成的json 字符串
            result.put("valuesSkuJson",valuesSkuJson);
        }));

        //更新商品incrHotScore
        CompletableFuture<Void> incrHotScoreCompletableFuture = CompletableFuture.runAsync(() -> {
            listFeignClient.incrHotScore(skuId);
        });

        CompletableFuture.allOf(skuInfoCompletableFuture,
                spuSaleAttrCompletableFuture,
                categoryViewCompletableFuture,
                priceCompletableFuture,
                valuesSkuJsonCompletableFuture,
                incrHotScoreCompletableFuture).join();


        return result;


    }
}
