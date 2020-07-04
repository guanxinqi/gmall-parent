package com.atguigu.gmall.payment.service;

import com.alipay.api.AlipayApiException;

/**
 * @author mqx
 * @date 2020/6/29 16:40
 */
public interface AlipayService {

    String createaliPay(Long orderId) throws AlipayApiException;
    //退款
    boolean refund(Long orderId);
    /***
     * 关闭交易
     * @param orderId
     * @return
     */
    Boolean closePay(Long orderId);
    /**
     * 根据订单查询是否支付成功！
     * @param orderId
     * @return
     */
    Boolean checkPayment(Long orderId);
}
