package com.atguigu.gmall.order.receiver;


import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.service.RabbitService;
import com.atguigu.gmall.model.enums.PaymentStatus;
import com.atguigu.gmall.model.enums.ProcessStatus;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.model.payment.PaymentInfo;
import com.atguigu.gmall.order.service.OrderService;

import com.atguigu.gmall.payment.client.PaymentFeignClient;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OrderReceiver {

    @Autowired
    private OrderService orderService;

    @Autowired
    private RabbitService rabbitService;

    @Autowired
    private PaymentFeignClient paymentFeignClient;

    /**
     * 取消订单消费者
     * 延迟队列，不能再这里做交换机与队列绑定
     * @param orderId
     * @throws IOException
     */
    @RabbitListener(queues = MqConst.QUEUE_ORDER_CANCEL)
    public void orderCancel(Long orderId, Message message, Channel channel) throws IOException {
        // 判断订单Id 是否为空
        if(null!=orderId){
            // 根据订单Id 查询订单表中是否有当前记录
            OrderInfo orderInfo = orderService.getById(orderId);
            if (null!=orderInfo && orderInfo.getOrderStatus().equals(ProcessStatus.UNPAID.getOrderStatus().name())){
                // 先关闭paymentInfo 后关闭orderInfo,因为 支付成功之后，异步回调先修改的paymentInfo,然后在发送的异步通知修改订单的状态。
                // 关闭流程，应该先看电商平台的交易记录中是否有数据，如果有则关闭。
                PaymentInfo paymentInfo = paymentFeignClient.getPaymentInfo(orderInfo.getOutTradeNo());
                // 判断电商交易记录 ,交易记录表中有数据，那么用户一定走到了二维码那一步。
                if (null!=paymentInfo && paymentInfo.getPaymentStatus().equals(PaymentStatus.UNPAID.name())){
                    // 检查支付宝中是否有交易记录
                    Boolean flag = paymentFeignClient.checkPayment(orderId);
                    // 说明用户在支付宝中产生了交易记录，用户是扫了。
                    if (flag){
                        // 关闭支付宝
                        Boolean result = paymentFeignClient.closePay(orderId);
                        // 判断是否关闭成功
                        if (result){
                            // 关闭支付宝的订单成功 关闭 OrderInfo 表,paymentInfo
                            orderService.execExpiredOrder(orderId,"2");
                        }else {
                            // 关闭支付宝的订单失败，如果用户付款成功了，那么我们调用关闭接口是失败！
                            // 如果成功走正常流程
                            // 很极端，测试。。。。。
                            rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_PAYMENT_PAY,MqConst.ROUTING_PAYMENT_PAY,orderId);
                        }
                    }else {
                        // 说明用户根本没有扫描，说明到了二维码
                        // 关闭支付宝的订单成功 关闭 OrderInfo 表,paymentInfo
                        orderService.execExpiredOrder(orderId,"2");
                    }
                }else {
                    // 说明paymentInfo 中根本就没有数据 ，没有数据，那么就只需要关闭orderInfo,
                    orderService.execExpiredOrder(orderId,"1");
                }
            }
        }
//            // 手动确认消息已经处理了。
        channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
    }


    /**
     * 订单支付，更改订单状态与通知扣减库存
     * @param orderId
     * @throws IOException
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MqConst.QUEUE_PAYMENT_PAY, durable = "true"),
            exchange = @Exchange(value = MqConst.EXCHANGE_DIRECT_PAYMENT_PAY),
            key = {MqConst.ROUTING_PAYMENT_PAY}
    ))
    public void paySuccess(Long orderId, Message message, Channel channel) throws IOException {
        if (null != orderId){
            //防止重复消费
            OrderInfo orderInfo = orderService.getById(orderId);
            if(null != orderInfo && orderInfo.getOrderStatus().equals(ProcessStatus.UNPAID.getOrderStatus().name())) {
                // 支付成功！ 修改订单状态为已支付
                orderService.updateOrderStatus(orderId, ProcessStatus.PAID);

                // 发送消息，通知仓库
                orderService.sendOrderStatus(orderId);
            }
        }
        //手动确认
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }






}