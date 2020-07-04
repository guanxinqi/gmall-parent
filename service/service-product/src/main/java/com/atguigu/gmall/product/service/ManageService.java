package com.atguigu.gmall.product.service;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.gmall.model.product.*;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface ManageService {

    /**
     * 查询所有的一级分类信息
     * @return
     */
    List<BaseCategory1> getCategory1();

    /**
     * 根据一级分类Id 查询二级分类数据
     * @param category1Id
     * @return
     */
    List<BaseCategory2> getCategory2(Long category1Id);

    /**
     * 根据二级分类Id 查询三级分类数据
     * @param category2Id
     * @return
     */
    List<BaseCategory3> getCategory3(Long category2Id);


    /**
     * 根据分类Id 获取平台属性数据
     * 接口说明：
     *      1，平台属性可以挂在一级分类、二级分类和三级分类
     *      2，查询一级分类下面的平台属性，传：category1Id，0，0；   取出该分类的平台属性
     *      3，查询二级分类下面的平台属性，传：category1Id，category2Id，0；
     *         取出对应一级分类下面的平台属性与二级分类对应的平台属性
     *      4，查询三级分类下面的平台属性，传：category1Id，category2Id，category3Id；
     *         取出对应一级分类、二级分类与三级分类对应的平台属性
     *
     * @param category1Id
     * @param category2Id
     * @param category3Id
     * @return
     */
    List<BaseAttrInfo> getAttrInfoList(Long category1Id, Long category2Id, Long category3Id);

    void saveAttrInfo(BaseAttrInfo baseAttrInfo);

    BaseAttrInfo getAttrInfo(Long attrId);

    /**
     * spu分页查询
     * @param pageParam
     * @param spuInfo
     * @return
     */
    IPage<SpuInfo> selectPage(Page<SpuInfo> pageParam, SpuInfo spuInfo);

    //获取所有销售属性
    List<BaseSaleAttr> getBaseSaleAttrList();

    //保存SpuInfo数据
    void saveSpuInfo(SpuInfo spuInfo);

    //查询图片列表
    List<SpuImage> getSpuImageList(Long spuId);

    List<SpuSaleAttr> getSpuSaleAttrList(Long spuId);

    //sku大保存
    void saveSkuInfo(SkuInfo skuInfo);
    /**
     * SKU分页列表
     * @param pageParam
     * @return
     */
    IPage<SkuInfo> selectPage(Page<SkuInfo> pageParam);

    //上架商品
    void onSale(Long skuId);
    //下架
    void cancelSale(Long skuId);



    /**
     * 根据skuI 查询数据
     * @param skuId
     * @return
     */
    SkuInfo getSkuInfo(Long skuId);


    /**
     * 根据三级分类Id 来获取分类名称
     * @param category3Id
     * @return
     */
    BaseCategoryView getBaseCategoryViewBycategory3Id(Long category3Id);

    /**
     * 通过skuId 查询价格
     * @param skuId
     * @return
     */
    BigDecimal getSkuPriceBySkuId(Long skuId);

    /**
     * 根据skuId spuId 查询销售属性集合数据
     * @param skuId
     * @param spuId
     * @return
     */
    List<SpuSaleAttr> getSpuSaleAttrListCheckBySku(Long skuId, Long spuId);

    /**
     * 根据spuId 查询数据
     * map.put("value_ids","skuId")
     * @param spuId
     * @return
     */
    Map getSkuValueIdsMap(Long spuId);

    /**
     * 获取全部分类信息
     * @return
     */
    List<JSONObject> getBaseCategoryList();

    /**
     * 通过品牌Id 来查询数据
     * @param tmId
     * @return
     */
    BaseTrademark getTrademarkByTmId(Long tmId);

    /**
     * 通过skuId 集合来查询数据
     * @param skuId
     * @return
     */
    List<BaseAttrInfo> getAttrList(Long skuId);
}
