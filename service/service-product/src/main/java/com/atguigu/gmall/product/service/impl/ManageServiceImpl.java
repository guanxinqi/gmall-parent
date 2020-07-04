package com.atguigu.gmall.product.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.gmall.common.cache.GmallCache;
import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.common.service.RabbitService;
import com.atguigu.gmall.model.product.*;
import com.atguigu.gmall.product.mapper.*;
import com.atguigu.gmall.product.service.ManageService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class ManageServiceImpl implements ManageService {
    @Autowired
    private BaseCategory1Mapper baseCategory1Mapper;

    @Autowired
    private BaseCategory2Mapper baseCategory2Mapper;

    @Autowired
    private BaseCategory3Mapper baseCategory3Mapper;

    @Autowired
    private BaseAttrInfoMapper baseAttrInfoMapper;

    @Autowired
    private BaseAttrValueMapper baseAttrValueMapper;

    @Autowired
    private SpuInfoMapper spuInfoMapper;

    @Autowired
    private BaseSaleAttrMapper baseSaleAttrMapper;

    @Autowired
    private SpuImageMapper   spuImageMapper ;

    @Autowired
    private SpuSaleAttrMapper   spuSaleAttrMapper ;

    @Autowired
    private SpuSaleAttrValueMapper  spuSaleAttrValueMapper;

    @Autowired
    private SkuImageMapper   skuImageMapper;

    @Autowired
    private SkuAttrValueMapper   skuAttrValueMapper;

    @Autowired
    private SkuSaleAttrValueMapper   skuSaleAttrValueMapper;

    @Autowired
    private SkuInfoMapper   skuInfoMapper;

    @Autowired
    private BaseCategoryViewMapper    baseCategoryViewMapper;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private RabbitService rabbitService;

    @Override
    public List<BaseCategory1> getCategory1() {
        return baseCategory1Mapper.selectList(null);
    }

    @Override
    public List<BaseCategory2> getCategory2(Long category1Id) {
        // select * from baseCategory2 where Category1Id = ?
        QueryWrapper queryWrapper = new QueryWrapper<BaseCategory2>();
        queryWrapper.eq("category1_id",category1Id);
        List<BaseCategory2> baseCategory2List = baseCategory2Mapper.selectList(queryWrapper);
        return baseCategory2List;
    }

    @Override
    public List<BaseCategory3> getCategory3(Long category2Id) {
        // select * from baseCategory3 where Category2Id = ?
        QueryWrapper queryWrapper = new QueryWrapper<BaseCategory3>();
        queryWrapper.eq("category2_id",category2Id);
        return baseCategory3Mapper.selectList(queryWrapper);
    }

    @Override
    public List<BaseAttrInfo> getAttrInfoList(Long category1Id, Long category2Id, Long category3Id) {
        // 调用mapper：
        return baseAttrInfoMapper.selectBaseAttrInfoList(category1Id, category2Id, category3Id);
    }

    @Override
    @Transactional
    public void saveAttrInfo(BaseAttrInfo baseAttrInfo) {
        // 什么情况下 是添加，什么情况下是更新，修改 根据baseAttrInfo 的Id
        // baseAttrInfo
        if (baseAttrInfo.getId() != null) {
            // 修改数据
            baseAttrInfoMapper.updateById(baseAttrInfo);
        } else {
            // 新增
            // baseAttrInfo 插入数据
            baseAttrInfoMapper.insert(baseAttrInfo);
        }

        // baseAttrValue 平台属性值
        // 修改：通过先删除{baseAttrValue}，在新增的方式！
        // 删除条件：baseAttrValue.attrId = baseAttrInfo.id
        QueryWrapper queryWrapper = new QueryWrapper<BaseAttrValue>();
        queryWrapper.eq("attr_id", baseAttrInfo.getId());
        baseAttrValueMapper.delete(queryWrapper);

        // 获取页面传递过来的所有平台属性值数据
        List<BaseAttrValue> attrValueList = baseAttrInfo.getAttrValueList();
        if (attrValueList != null && attrValueList.size() > 0) {
            // 循环遍历
            for (BaseAttrValue baseAttrValue : attrValueList) {
                // 获取平台属性Id 给attrId
                baseAttrValue.setAttrId(baseAttrInfo.getId()); // ?
                baseAttrValueMapper.insert(baseAttrValue);
            }
        }
    }

    @Override
    public BaseAttrInfo getAttrInfo(Long attrId) {
        BaseAttrInfo baseAttrInfo = baseAttrInfoMapper.selectById(attrId);

        QueryWrapper queryWrapper = new QueryWrapper<BaseAttrValue>();
        queryWrapper.eq("attr_id", attrId);
        List<BaseAttrValue> baseAttrValueList = baseAttrValueMapper.selectList(queryWrapper);
        baseAttrInfo.setAttrValueList(baseAttrValueList);

        return baseAttrInfo;

    }

    @Override
    public IPage<SpuInfo> selectPage(Page<SpuInfo> pageParam, SpuInfo spuInfo) {
        QueryWrapper<SpuInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("category3_id", spuInfo.getCategory3Id());
        queryWrapper.orderByDesc("id");

        return spuInfoMapper.selectPage(pageParam, queryWrapper);
    }

    @Override
    public List<BaseSaleAttr> getBaseSaleAttrList() {
        return baseSaleAttrMapper.selectList(null);
    }

    @Override
    @Transactional
    public void saveSpuInfo(SpuInfo spuInfo) {
//        spuInfo 商品表
        spuInfoMapper.insert(spuInfo);
//        spuImage 商品图片表
        List<SpuImage> spuImageList = spuInfo.getSpuImageList();
        if (spuImageList != null && spuImageList.size() > 0) {
            for (SpuImage spuImage : spuImageList) {
                spuImage.setSpuId(spuInfo.getId());
                spuImageMapper.insert(spuImage);
            }
        }
//        spuSaleAttr 销售属性表
        List<SpuSaleAttr> spuSaleAttrList = spuInfo.getSpuSaleAttrList();
        if (spuSaleAttrList != null && spuSaleAttrList.size() > 0) {
            for (SpuSaleAttr spuSaleAttr : spuSaleAttrList) {
                spuSaleAttr.setSpuId(spuInfo.getId());
                spuSaleAttrMapper.insert(spuSaleAttr);

                //        spuSaleAttrValue 销售属性值表
                List<SpuSaleAttrValue> spuSaleAttrValueList = spuSaleAttr.getSpuSaleAttrValueList();
                if (spuSaleAttrValueList != null && spuSaleAttrValueList.size() > 0) {
                    for (SpuSaleAttrValue spuSaleAttrValue : spuSaleAttrValueList) {
                        spuSaleAttrValue.setSpuId(spuInfo.getId());
                        spuSaleAttrValue.setSaleAttrName(spuSaleAttr.getSaleAttrName());
                        spuSaleAttrValueMapper.insert(spuSaleAttrValue);
                    }
                }
            }
        }
    }


    @Override
    public List<SpuImage> getSpuImageList(Long spuId) {
        QueryWrapper<SpuImage> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("spu_id", spuId);
        return spuImageMapper.selectList(queryWrapper);
    }

    @Override
    public List<SpuSaleAttr> getSpuSaleAttrList(Long spuId) {
        return spuSaleAttrMapper.selectSpuSaleAttrList(spuId);
    }


    @Override
    @Transactional
    public void saveSkuInfo(SkuInfo skuInfo) {
    /*
        skuInfo 库存单元表 --- spuInfo！
        skuImage 库存单元图片表 --- spuImage!
        skuSaleAttrValue sku销售属性值表{sku与销售属性值的中间表} --- skuInfo ，spuSaleAttrValue
        skuAttrValue sku与平台属性值的中间表 --- skuInfo ，baseAttrValue
     */
        skuInfoMapper.insert(skuInfo);
        List<SkuImage> skuImageList = skuInfo.getSkuImageList();
        if (skuImageList != null && skuImageList.size() > 0) {

            // 循环遍历
            for (SkuImage skuImage : skuImageList) {
                skuImage.setSkuId(skuInfo.getId());
                skuImageMapper.insert(skuImage);
            }
        }

        List<SkuSaleAttrValue> skuSaleAttrValueList = skuInfo.getSkuSaleAttrValueList();
        // 调用判断集合方法
        if (!CollectionUtils.isEmpty(skuSaleAttrValueList)) {
            for (SkuSaleAttrValue skuSaleAttrValue : skuSaleAttrValueList) {
                skuSaleAttrValue.setSkuId(skuInfo.getId());
                skuSaleAttrValue.setSpuId(skuInfo.getSpuId());
                skuSaleAttrValueMapper.insert(skuSaleAttrValue);
            }
        }

        List<SkuAttrValue> skuAttrValueList = skuInfo.getSkuAttrValueList();
        if (!CollectionUtils.isEmpty(skuAttrValueList)) {
            for (SkuAttrValue skuAttrValue : skuAttrValueList) {
                skuAttrValue.setSkuId(skuInfo.getId());
                skuAttrValueMapper.insert(skuAttrValue);
            }
        }


        //商品上架
        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_GOODS, MqConst.ROUTING_GOODS_UPPER, skuInfo.getId());
}

    @Override
    public IPage<SkuInfo> selectPage(Page<SkuInfo> pageParam) {
        QueryWrapper<SkuInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByDesc("id");

        IPage<SkuInfo> page = skuInfoMapper.selectPage(pageParam, queryWrapper);
        return page;
    }
    //上架
    @Override
    @Transactional
    public void onSale(Long skuId) {
        // 更改销售状态
        SkuInfo skuInfoUp = new SkuInfo();
        skuInfoUp.setId(skuId);
        skuInfoUp.setIsSale(1);
        skuInfoMapper.updateById(skuInfoUp);

//商品上架
        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_GOODS, MqConst.ROUTING_GOODS_UPPER, skuId);
    }
    //下架
    @Override
    @Transactional
    public void cancelSale(Long skuId) {
        // 更改销售状态
        SkuInfo skuInfoUp = new SkuInfo();
        skuInfoUp.setId(skuId);
        skuInfoUp.setIsSale(0);
        skuInfoMapper.updateById(skuInfoUp);

        //商品上架
        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_GOODS, MqConst.ROUTING_GOODS_UPPER, skuId);
    }


    @Override
    @GmallCache(prefix = "sku")
    public SkuInfo getSkuInfo(Long skuId) {
        // ctrl+alt+m
        // return getSkuInfoRedisson(skuId);
        return getSkuInfoDB(skuId);
    }

    private SkuInfo getSkuInfoRedisson(Long skuId) {
        // 在此获取skuInfo 的时候，先查询缓存，如果缓存中有数据，则查询，没有查询数据库并放入缓存!
        SkuInfo skuInfo = null;
        try {
            // 先判断缓存中是否有数据，查询缓存必须知道缓存的key是什么！
            // 定义缓存的key 商品详情的缓存key=sku:skuId:info
            String skuKey = RedisConst.SKUKEY_PREFIX+skuId+RedisConst.SKUKEY_SUFFIX;
            // 根据key 获取缓存中的数据
            // 如果查询一个不存在的数据，那么缓存中应该是一个空对象{这个对象有地址，但是属性Id，price 等没有值}
            skuInfo = (SkuInfo) redisTemplate.opsForValue().get(skuKey);
            // 存储数据为什么使用String ，存储对象的时候建议使用Hash---{hset(skuKey,字段名,字段名所对应的值); 便于对当前对象中属性修改}
            // 对于商品详情来讲：我们只做显示，并没有修改。所以此处可以使用String 来存储!
            if (skuInfo==null){
                // 从数据库中获取数据，防止缓存击穿做分布式锁
                // 定义分布式锁的key lockKey=sku:skuId:lock
                String lockKey = RedisConst.SKUKEY_PREFIX+skuId+RedisConst.SKULOCK_SUFFIX;
                // 使用redisson
                RLock lock = redissonClient.getLock(lockKey);
                // 尝试加锁，最多等待100秒，上锁以后10秒自动解锁
                boolean res = lock.tryLock(RedisConst.SKULOCK_EXPIRE_PX2, RedisConst.SKULOCK_EXPIRE_PX1, TimeUnit.SECONDS);
                if (res) {
                    try {
                        // 从数据库中获取数据
                        skuInfo = getSkuInfoDB(skuId);
                        if (skuInfo==null){
                            // 为了防止缓存穿透，设置一个空对象放入缓存,这个时间建议不要太长！
                            SkuInfo skuInfo1 = new SkuInfo();
                            // 放入缓存
                            redisTemplate.opsForValue().set(skuKey,skuInfo1,RedisConst.SKUKEY_TEMPORARY_TIMEOUT,TimeUnit.SECONDS);
                            // 返回数据
                            return  skuInfo1;
                        }
                        // 从数据库中获取到了数据，放入缓存
                        redisTemplate.opsForValue().set(skuKey,skuInfo,RedisConst.SKUKEY_TIMEOUT,TimeUnit.SECONDS);
                        return skuInfo;
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        // 解锁
                        lock.unlock();
                    }
                }else {
                    // 此时的线程并没有获取到分布式锁，应该等待,
                    Thread.sleep(1000);
                    // 等待完成之后，还需要查询数据！
                    return getSkuInfo(skuId);
                }
            }else {
                // 表示缓存中有数据了
                // 弯！稍加严禁一点：
                //            if (skuInfo.getId()==null){ // 这个对象有地址，但是属性Id，price 等没有值！
                //                return null;
                //            }
                // 缓存中有数据，应该直接返回即可！
                return skuInfo; // 情况一：这个对象有地址，但是属性Id，price 等没有值！  情况二：就是既有地址，又有属性值！

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        // 如何中途发送了异常：数据库挺一下！
        return  getSkuInfoDB(skuId);
    }

    private SkuInfo getSkuInfoRedis(Long skuId) {
        // 在此获取skuInfo 的时候，先查询缓存，如果缓存中有数据，则查询，没有查询数据库并放入缓存!
        SkuInfo skuInfo = null;
        try {
            // 先判断缓存中是否有数据，查询缓存必须知道缓存的key是什么！
            // 定义缓存的key 商品详情的缓存key=sku:skuId:info
            String skuKey = RedisConst.SKUKEY_PREFIX+skuId+RedisConst.SKUKEY_SUFFIX;
            // 根据key 获取缓存中的数据
            // 如果查询一个不存在的数据，那么缓存中应该是一个空对象{这个对象有地址，但是属性Id，price 等没有值}
            skuInfo = (SkuInfo) redisTemplate.opsForValue().get(skuKey);
            // 存储数据为什么使用String ，存储对象的时候建议使用Hash---{hset(skuKey,字段名,字段名所对应的值); 便于对当前对象中属性修改}
            // 对于商品详情来讲：我们只做显示，并没有修改。所以此处可以使用String 来存储!
            if (skuInfo==null){
                // 应该获取数据库中的数据，放入缓存！分布式锁！为了防止缓存击穿
                // 定义分布式锁的key lockKey=sku:skuId:lock
                String lockKey = RedisConst.SKUKEY_PREFIX+skuId+RedisConst.SKULOCK_SUFFIX;
                // 还需要一个uuId，做为锁的值value
                String uuid= UUID.randomUUID().toString();
                // 开始上锁
                Boolean isExist = redisTemplate.opsForValue().setIfAbsent(lockKey, uuid, RedisConst.SKULOCK_EXPIRE_PX1, TimeUnit.SECONDS);
                // 如果返回true 获取到分布式锁！
                if (isExist){
                    System.out.println("获取到锁！");
                    // 去数据库获取数据，并放入缓存！
                    // 传入的skuId 在数据库中一定存在么？
                    skuInfo = getSkuInfoDB(skuId);
                    if (skuInfo==null){
                        // 为了防止缓存穿透，设置一个空对象放入缓存,这个时间建议不要太长！
                        SkuInfo skuInfo1 = new SkuInfo();
                        // 放入缓存
                        redisTemplate.opsForValue().set(skuKey,skuInfo1,RedisConst.SKUKEY_TEMPORARY_TIMEOUT,TimeUnit.SECONDS);
                        // 返回数据
                        return  skuInfo1;
                    }
                    // 从数据库中查询出来不是空！放入缓存
                    redisTemplate.opsForValue().set(skuKey,skuInfo,RedisConst.SKUKEY_TIMEOUT,TimeUnit.SECONDS);
                    // 删除锁！ 使用lua 脚本删除！
                    String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
                    // 如何操作：
                    // 构建RedisScript 数据类型需要确定一下，默认情况下返回的Object
                    DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
                    // 指定好返回的数据类型
                    redisScript.setResultType(Long.class);
                    // 指定好lua 脚本
                    redisScript.setScriptText(script);
                    // 第一个参数存储的RedisScript  对象，第二个参数指的锁的key，第三个参数指的key所对应的值
                    redisTemplate.execute(redisScript, Arrays.asList(lockKey),uuid);

                    // 返回正常数据
                    return skuInfo;
                }else {
                    // 此时的线程并没有获取到分布式锁，应该等待,
                    Thread.sleep(1000);
                    // 等待完成之后，还需要查询数据！
                    return getSkuInfo(skuId);
                }
            }else {
                // 弯！稍加严禁一点：
                //            if (skuInfo.getId()==null){ // 这个对象有地址，但是属性Id，price 等没有值！
                //                return null;
                //            }
                // 缓存中有数据，应该直接返回即可！
                return skuInfo; // 情况一：这个对象有地址，但是属性Id，price 等没有值！  情况二：就是既有地址，又有属性值！
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        // 如何中途发送了异常：数据库挺一下！
        return  getSkuInfoDB(skuId);
    }
    // 提取方法
    private SkuInfo getSkuInfoDB(Long skuId) {
        // select * from sku_info where id = skuId
        // skuId=1000 在数据库中根本不存在！skuInfo 应该是空对象
        SkuInfo skuInfo = skuInfoMapper.selectById(skuId);

        if (null!=skuInfo){
            // 查询Sku图片赋值给skuInfo 对象，那么这个时候，skuInfo 对象中 sku基本数据，sku图片数据
            // select * from sku_image where sku_id = skuId
            List<SkuImage> skuImageList = skuImageMapper.selectList(new QueryWrapper<SkuImage>().eq("sku_id", skuId));
            skuInfo.setSkuImageList(skuImageList);
        }
        return skuInfo;
    }



    @Override
    @GmallCache(prefix = "baseCategoryView")
    public BaseCategoryView getBaseCategoryViewBycategory3Id(Long category3Id) {
        return baseCategoryViewMapper.selectById(category3Id);
    }

    @Override
    @GmallCache(prefix = "Price")
    public BigDecimal getSkuPriceBySkuId(Long skuId) {
        SkuInfo skuInfo = skuInfoMapper.selectById(skuId);
        if (null!=skuInfo){
            return skuInfo.getPrice();
        }
        return new BigDecimal(0);
    }

    @Override
    @GmallCache(prefix = "saleAttrListCheck")
    public List<SpuSaleAttr> getSpuSaleAttrListCheckBySku(Long skuId, Long spuId) {
        return spuSaleAttrMapper.selectSpuSaleAttrListCheckBySku(skuId,spuId);
    }

    @Override
    @GmallCache(prefix = "valueId")
    public Map getSkuValueIdsMap(Long spuId) {
        // 调用mapper 自定义方法获取数据，将数据查询之后直接放入List。
        HashMap<Object, Object> map = new HashMap<>();
        /*
            select sv.sku_id, group_concat(sv.sale_attr_value_id order by sp.base_sale_attr_id asc separator '|')
                value_ids from sku_sale_attr_value sv
                inner  join spu_sale_attr_value  sp on sp.id = sv.sale_attr_value_id
                where sv.spu_id = 12
                group by sku_id;

            执行出来的结果应该是List<Map>
            map.put("55|57","30") skuSaleAttrValueMapper
         */
        List<Map> mapList = skuSaleAttrValueMapper.getSaleAttrValuesBySpu(spuId);
        // 获取到数据以后。开始循环遍历集合中的每条数据
        if (null!=mapList && mapList.size()>0){
            for (Map skuMaps : mapList) {
                // map.put("55|57","30")
                map.put(skuMaps.get("value_ids"),skuMaps.get("sku_id"));
            }
        }
        return map;
    }

    @Override
    @GmallCache(prefix = "index")
    public List<JSONObject> getBaseCategoryList() {
        // 声明几个json 集合
        ArrayList<JSONObject> list = new ArrayList<>();
        // 声明获取所有分类数据集合
        List<BaseCategoryView> baseCategoryViewList = baseCategoryViewMapper.selectList(null);
        // 循环上面的集合并安一级分类Id 进行分组
        Map<Long, List<BaseCategoryView>> category1Map  = baseCategoryViewList.stream().collect(Collectors.groupingBy(BaseCategoryView::getCategory1Id));
        int index = 1;
        // 获取一级分类下所有数据
        for (Map.Entry<Long, List<BaseCategoryView>> entry1  : category1Map.entrySet()) {
            // 获取一级分类Id
            Long category1Id  = entry1.getKey();
            // 获取一级分类下面的所有集合
            List<BaseCategoryView> category2List1  = entry1.getValue();
            //
            JSONObject category1 = new JSONObject();
            category1.put("index", index);
            category1.put("categoryId",category1Id);
            // 一级分类名称
            category1.put("categoryName",category2List1.get(0).getCategory1Name());
            // 变量迭代
            index++;
            // 循环获取二级分类数据
            Map<Long, List<BaseCategoryView>> category2Map  = category2List1.stream().collect(Collectors.groupingBy(BaseCategoryView::getCategory2Id));
            // 声明二级分类对象集合
            List<JSONObject> category2Child = new ArrayList<>();
            // 循环遍历
            for (Map.Entry<Long, List<BaseCategoryView>> entry2  : category2Map.entrySet()) {
                // 获取二级分类Id
                Long category2Id  = entry2.getKey();
                // 获取二级分类下的所有集合
                List<BaseCategoryView> category3List  = entry2.getValue();
                // 声明二级分类对象
                JSONObject category2 = new JSONObject();

                category2.put("categoryId",category2Id);
                category2.put("categoryName",category3List.get(0).getCategory2Name());
                // 添加到二级分类集合
                category2Child.add(category2);

                List<JSONObject> category3Child = new ArrayList<>();

                // 循环三级分类数据
                category3List.stream().forEach(category3View -> {
                    JSONObject category3 = new JSONObject();
                    category3.put("categoryId",category3View.getCategory3Id());
                    category3.put("categoryName",category3View.getCategory3Name());

                    category3Child.add(category3);
                });

                // 将三级数据放入二级里面
                category2.put("categoryChild",category3Child);

            }
            // 将二级数据放入一级里面
            category1.put("categoryChild",category2Child);
            list.add(category1);
        }
        return list;
    }


    @Autowired
    private BaseTrademarkMapper baseTrademarkMapper;

    @Override
    public BaseTrademark getTrademarkByTmId(Long tmId) {
        return baseTrademarkMapper.selectById(tmId);
    }


    @Override
    public List<BaseAttrInfo> getAttrList(Long skuId) {

        return baseAttrInfoMapper.selectBaseAttrInfoListBySkuId(skuId);
    }
}
