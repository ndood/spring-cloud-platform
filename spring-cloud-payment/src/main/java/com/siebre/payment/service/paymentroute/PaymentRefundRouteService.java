package com.siebre.payment.service.paymentroute;

import com.siebre.basic.applicationcontext.SpringContextUtil;
import com.siebre.payment.entity.enums.PaymentInterfaceType;
import com.siebre.payment.entity.paymentchannel.PaymentChannel;
import com.siebre.payment.entity.paymentinterface.PaymentInterface;
import com.siebre.payment.entity.paymentorder.PaymentOrder;
import com.siebre.payment.entity.paymenttransaction.PaymentTransaction;
import com.siebre.payment.entity.paymentway.PaymentWay;
import com.siebre.payment.mapper.paymentchannel.PaymentChannelMapper;
import com.siebre.payment.mapper.paymentinterface.PaymentInterfaceMapper;
import com.siebre.payment.service.paymenthandler.basic.paymentrefund.AbstractPaymentRefundComponent;
import com.siebre.payment.service.paymenthandler.paymentrefund.PaymentRefundRequest;
import com.siebre.payment.service.paymenthandler.paymentrefund.PaymentRefundResponse;
import com.siebre.payment.service.paymentorder.PaymentOrderService;
import com.siebre.payment.service.paymenttransaction.PaymentTransactionService;
import com.siebre.payment.service.paymentway.PaymentWayService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Created by AdamTang on 2017/4/22.
 * Project:siebre-cloud-platform
 * Version:1.0
 * 退款路由
 * 通过路由交给具体的退款handler处理退款
 */
@Service
public class PaymentRefundRouteService {

    private Logger logger = LoggerFactory.getLogger(PaymentRefundRouteService.class);

    @Autowired
    private PaymentOrderService paymentOrderService;

    @Autowired
    private PaymentWayService paymentWayService;

    @Autowired
    private PaymentTransactionService paymentTransactionService;

    public PaymentRefundResponse route(PaymentRefundRequest paymentRefundRequest) {

        PaymentOrder paymentOrder = paymentOrderService.queryPaymentOrder(paymentRefundRequest.getRefundApplication().getOrderNumber());
        paymentRefundRequest.setPaymentOrder(paymentOrder);

        PaymentTransaction paymentTransaction = paymentTransactionService.getSuccessPaidPaymentTransaction(paymentOrder.getOrderNumber());
        paymentRefundRequest.setPaymentTransaction(paymentTransaction);

        PaymentWay paymentWay = paymentWayService.getPaymentWay(paymentTransaction.getPaymentWay().getCode());

        //针对同一渠道下不同支付方式使用统一退款接口的情况，需要做处理。先在该支付方式下查找，是否存在paymentInterface，如果不存在，则在该支付方式所在渠道下查找paymentInterface
        PaymentInterface paymentInterface = paymentWayService.getPaymentInterface(paymentWay.getCode(), PaymentInterfaceType.REFUND);
        if (paymentInterface == null) {
            logger.info("支付方式{}下没有找到对应的退款handler,在该支付方式对应的渠道下查找",paymentWay.getName());
            List<PaymentWay> ways = paymentWayService.getPaymentWayByChannelId(paymentWay.getPaymentChannelId());
            for (PaymentWay way : ways){
                paymentInterface = paymentWayService.getPaymentInterface(way.getCode(), PaymentInterfaceType.REFUND);
                if(paymentInterface != null){
                    //使用该interface对应的paymentway
                    paymentWay = paymentWayService.getPaymentWay(way.getCode());
                    break;
                }
            }
        }

        AbstractPaymentRefundComponent paymentRefundHandler = (AbstractPaymentRefundComponent) SpringContextUtil.getBean(paymentInterface.getHandlerBeanName());

        PaymentRefundResponse paymentRefundResponse = paymentRefundHandler.handle(paymentRefundRequest, paymentTransaction, paymentOrder, paymentWay, paymentInterface);


        return paymentRefundResponse;
    }
}
