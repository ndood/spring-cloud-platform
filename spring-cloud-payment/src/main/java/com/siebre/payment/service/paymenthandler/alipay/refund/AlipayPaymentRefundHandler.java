package com.siebre.payment.service.paymenthandler.alipay.refund;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.siebre.basic.utils.JsonUtil;
import com.siebre.payment.entity.enums.EncryptionMode;
import com.siebre.payment.entity.enums.PaymentTransactionStatus;
import com.siebre.payment.entity.enums.RefundApplicationStatus;
import com.siebre.payment.entity.paymentinterface.PaymentInterface;
import com.siebre.payment.entity.paymentorder.PaymentOrder;
import com.siebre.payment.entity.paymenttransaction.PaymentTransaction;
import com.siebre.payment.entity.paymentway.PaymentWay;
import com.siebre.payment.entity.refundapplication.RefundApplication;
import com.siebre.payment.service.paymenthandler.alipay.sdk.AlipayConfig;
import com.siebre.payment.service.paymenthandler.basic.paymentrefund.AbstractPaymentRefundComponent;
import com.siebre.payment.serviceinterface.paymenthandler.paymentrefund.PaymentRefundRequest;
import com.siebre.payment.serviceinterface.paymenthandler.paymentrefund.PaymentRefundResponse;

/**
 * Created by AdamTang on 2017/4/24.
 * Project:siebre-cloud-platform
 * Version:1.0
 */
@Service("alipayPaymentRefundHandler")
public class AlipayPaymentRefundHandler extends AbstractPaymentRefundComponent {
    @Override
    protected PaymentRefundResponse handleInternal(PaymentRefundRequest paymentRefundRequest, PaymentTransaction paymentTransaction, PaymentOrder paymentOrder, PaymentWay paymentWay, PaymentInterface paymentInterface) {
        AlipayClient alipayClient = new DefaultAlipayClient(paymentWay.getPaymentGatewayUrl(),
                paymentWay.getAppId(), paymentWay.getSecretKey(), "json", AlipayConfig.input_charset_utf,
                paymentWay.getPublicKey(), EncryptionMode.RSA.getDescription()); //获得初始化的AlipayClient

        AlipayTradeRefundRequest alipayRequest = buildAlipayRefundRequest(paymentRefundRequest, paymentWay, paymentTransaction);

        return processRefund(paymentRefundRequest, alipayClient, alipayRequest);
    }

    /**
     * 构造请求参数
     *
     * @param paymentWay
     * @param paymentTransaction
     * @return
     */
    private AlipayTradeRefundRequest buildAlipayRefundRequest(PaymentRefundRequest paymentRefundRequest, PaymentWay paymentWay, PaymentTransaction paymentTransaction) {
        AlipayTradeRefundRequest alipayRequest = new AlipayTradeRefundRequest();

        alipayRequest.setBizContent(generateBizContent(paymentRefundRequest, paymentWay, paymentTransaction));

        return alipayRequest;
    }

    /**
     * 生成业务请求参数
     *
     * @return
     */
    private String generateBizContent(PaymentRefundRequest paymentRefundRequest, PaymentWay paymentWay, PaymentTransaction paymentTransaction) {
        Map<String, String> params = new HashMap<>();
        //订单支付时传入的商户订单号,不能和 trade_no同时为空
        params.put("out_trade_no", paymentRefundRequest.getOriginInternalNumber());
        //支付宝交易号，和商户订单号不能同时为空
        params.put("trade_no", paymentRefundRequest.getOriginExternalNumber());

        params.put("refund_amount", paymentTransaction.getPaymentAmount().setScale(2, BigDecimal.ROUND_HALF_UP).toString());
        params.put("refund_reason", paymentRefundRequest.getRefundApplication().getRequest());

        //标识一次退款请求，同一笔交易多次退款需要保证唯一，如需部分退款，则此参数必传。
        params.put("out_request_no", paymentTransaction.getInternalTransactionNumber());

        return JsonUtil.mapToJson(params);
    }

    private PaymentRefundResponse processRefund(PaymentRefundRequest paymentRefundRequest, AlipayClient alipayClient, AlipayTradeRefundRequest alipayRequest) {
        PaymentRefundResponse refundResponse = new PaymentRefundResponse();

        PaymentTransaction refundTransaction = paymentRefundRequest.getRefundTransaction();
        RefundApplication refundApplication = paymentRefundRequest.getRefundApplication();

        try {
            AlipayTradeRefundResponse response = alipayClient.execute(alipayRequest);
            refundResponse.setExternalTransactionNumber(response.getTradeNo());
            refundTransaction.setExternalTransactionNumber(response.getTradeNo());
            refundResponse.setReturnMessage(response.getMsg());
            if (response.isSuccess()) {
                refundTransaction.setPaymentStatus(PaymentTransactionStatus.SUCCESS);//退款交易调用成功
                refundApplication.setStatus(RefundApplicationStatus.SUCCESS);
                logger.info("调用成功");
            } else {
                refundTransaction.setPaymentStatus(PaymentTransactionStatus.FAILED);
                refundApplication.setStatus(RefundApplicationStatus.FAILED);
                logger.error("调用失败,失败原因={}",response.getMsg());
            }

        } catch (AlipayApiException e) {
            refundTransaction.setPaymentStatus(PaymentTransactionStatus.FAILED);
            refundApplication.setStatus(RefundApplicationStatus.FAILED);
            logger.error("支付宝退款接口调用异常", e);
        }

        refundResponse.setRefundApplication(refundApplication);
        refundResponse.setPaymentTransaction(refundTransaction);

        return refundResponse;
    }
}
