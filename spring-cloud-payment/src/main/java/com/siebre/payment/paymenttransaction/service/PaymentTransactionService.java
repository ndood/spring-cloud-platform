package com.siebre.payment.paymenttransaction.service;

import com.siebre.basic.query.PageInfo;
import com.siebre.basic.service.ServiceResult;
import com.siebre.payment.entity.enums.*;
import com.siebre.payment.paymentchannel.mapper.PaymentChannelMapper;
import com.siebre.payment.paymentorder.entity.PaymentOrder;
import com.siebre.payment.paymentorder.mapper.PaymentOrderMapper;
import com.siebre.payment.paymentorderitem.entity.PaymentOrderItem;
import com.siebre.payment.paymentorderitem.mapper.PaymentOrderItemMapper;
import com.siebre.payment.paymenttransaction.entity.PaymentTransaction;
import com.siebre.payment.paymenttransaction.mapper.PaymentTransactionMapper;
import com.siebre.payment.paymentway.entity.PaymentWay;
import com.siebre.payment.refundapplication.entity.RefundApplication;
import com.siebre.payment.refundapplication.mapper.RefundApplicationMapper;
import com.siebre.payment.serialnumber.service.SerialNumberService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Service
public class PaymentTransactionService {
	
	@Autowired
	private PaymentTransactionMapper paymentTransactionMapper;
	
	private Logger logger = LoggerFactory.getLogger(PaymentTransactionService.class);

    @Autowired
    private PaymentChannelMapper paymentChannelMapper;

    @Autowired
    private PaymentOrderMapper paymentOrderMapper;

    @Autowired
    private PaymentOrderItemMapper paymentOrderItemMapper;

    @Autowired
    private RefundApplicationMapper refundApplicationMapper;

    @Autowired
    protected SerialNumberService serialNumberService;

    /**
     * 更新交易和订单状态
     * 1.判断传进来的交易状态和数据库中的状态是否一致
     * 2.如果一致，退出该方法
     * 3.如果不一致，更新交易状态，更新订单状态
     *
     * @param transactionId
     * @param transactionStatus
     * @return
     */
    @Transactional("db")
    public void updateTransactionAndOrderStatus(Long transactionId, PaymentTransactionStatus transactionStatus, Long orderId, PaymentOrderPayStatus orderStatus) {
        PaymentTransaction transaction = paymentTransactionMapper.selectByPrimaryKey(transactionId);
        PaymentOrder paymentOrder = paymentOrderMapper.selectByPrimaryKey(orderId);

        if (PaymentTransactionStatus.SUCCESS.equals(transactionStatus)) {
            if (!PaymentTransactionStatus.SUCCESS.equals(transaction.getPaymentStatus())) {
                PaymentTransaction transaction1 = new PaymentTransaction();
                transaction1.setId(transactionId);
                transaction1.setPaymentStatus(transactionStatus);
                paymentTransactionMapper.updateByPrimaryKeySelective(transaction1);

            }
            if (!PaymentOrderPayStatus.PAID.equals(paymentOrder.getStatus())) {
                PaymentOrder paymentOrder1 = new PaymentOrder();
                paymentOrder1.setId(paymentOrder.getId());
                paymentOrder1.setStatus(PaymentOrderPayStatus.PAID);
                paymentOrderMapper.updateByPrimaryKeySelective(paymentOrder1);
            }
        } else if (PaymentTransactionStatus.FAILED.equals(transactionStatus)) {
            //如果查询结果返回失败，并且订单状态为支付中，更新订单状态为交易关闭
            if (PaymentTransactionStatus.PROCESSING.equals(transaction.getPaymentStatus())) {
                PaymentTransaction transaction1 = new PaymentTransaction();
                transaction1.setId(transactionId);
                transaction1.setPaymentStatus(PaymentTransactionStatus.CLOSED);
                paymentTransactionMapper.updateByPrimaryKeySelective(transaction1);
            }
            if (PaymentOrderPayStatus.PAYING.equals(paymentOrder.getStatus())) {
                PaymentOrder paymentOrder1 = new PaymentOrder();
                paymentOrder1.setId(paymentOrder.getId());
                paymentOrder1.setStatus(PaymentOrderPayStatus.CLOSED);
                paymentOrderMapper.updateByPrimaryKeySelective(paymentOrder1);
            }
        }

    }

    public List<PaymentTransaction> queryRefundTransaction(Long orderId) {
        return paymentTransactionMapper.selectTransaction(orderId, PaymentInterfaceType.REFUND);
    }

    /**
     * 根据transaction status获取对应的order status
     *
     * @param transactionStatus
     * @return
     */
    public static PaymentOrderPayStatus getOrderStatusByTransactionStatus(PaymentTransactionStatus transactionStatus) {
        PaymentOrderPayStatus status = null;
        if (PaymentTransactionStatus.SUCCESS.equals(transactionStatus)) {
            //交易支付成功状态对应订单支付成功
            status = PaymentOrderPayStatus.PAID;
        }
        //TODO  确定订单和交易状态的对应关系
        return status;
    }

    @Transactional("db")
    public PaymentTransaction createRefundPaymentTransaction(PaymentTransaction paymentTransaction, RefundApplication refundApplication) {
        paymentTransactionMapper.insertSelective(paymentTransaction);


        refundApplicationMapper.updateByPrimaryKeySelective(refundApplication);

        return paymentTransaction;
    }

    /**
     * 查找交易成功的PaymentTransaction  PS:交易成功的应该只有一个，应该做好交易状态的管理
     *
     * @param orderNumber
     * @return
     */
    public PaymentTransaction getSuccessPaidPaymentTransaction(String orderNumber) {
        List<PaymentTransaction> paymentTransactions = paymentTransactionMapper.queryPaymentTransaction(orderNumber, null, PaymentTransactionStatus.SUCCESS, PaymentInterfaceType.PAY, null, null, null, null);
        if (paymentTransactions != null && !paymentTransactions.isEmpty()) {
            return paymentTransactions.get(0);
        }
        return null;
    }

    /**
     * 查找正在支付中的PaymentTransaction   PS:交易中的应该只有一个，应该做好交易状态的管理
     *
     * @param orderNumber
     * @return
     */
    public PaymentTransaction getPayingTransaction(String orderNumber) {
        List<PaymentTransaction> paymentTransactions = paymentTransactionMapper.queryPaymentTransaction(orderNumber, null, PaymentTransactionStatus.PROCESSING, PaymentInterfaceType.PAY, null, null, null, null);
        if (paymentTransactions != null && !paymentTransactions.isEmpty()) {
            return paymentTransactions.get(0);
        }
        return null;
    }

    /**
     * 订单查询，找到唯一匹配的PaymentTransaction
     * 1.查找是否存在已完成的交易
     * 2.否则,查找是否存在正在交易中的交易
     * 3.否则,返回null
     *
     * @param orderNumber
     * @return
     */
    public PaymentTransaction getPaymentTransactionForQuery(String orderNumber) {
        PaymentTransaction paymentTransaction;
        paymentTransaction = getSuccessPaidPaymentTransaction(orderNumber);
        if (null != paymentTransaction) {
            return paymentTransaction;
        }
        paymentTransaction = getPayingTransaction(orderNumber);
        if (null != paymentTransaction) {
            return paymentTransaction;
        }
        return null;
    }

    /**
     * 根据已有的paymentOrder创建paymentTransaction
     *
     * @param paymentOrder
     */
    @Transactional("db")
    public PaymentTransaction createTransaction(PaymentOrder paymentOrder, PaymentWay paymentWay) {
        PaymentTransaction paymentTransaction = new PaymentTransaction();
        paymentTransaction.setPaymentStatus(PaymentTransactionStatus.PROCESSING);
        paymentTransaction.setInternalTransactionNumber(serialNumberService.nextValue("payment"));
        paymentTransaction.setPaymentWayId(paymentWay.getId());
        paymentTransaction.setPaymentChannelId(paymentWay.getPaymentChannel().getId());
        paymentTransaction.setCreateUser("lizhiqiang");
        paymentTransaction.setCreateDate(new Date());
        paymentTransaction.setPaymentOrderId(paymentOrder.getId());
        paymentTransaction.setPaymentAmount(paymentOrder.getTotalPremium());
        //创建的是支付的交易
        paymentTransaction.setInterfaceType(PaymentInterfaceType.PAY);
        paymentTransaction.setCreateDate(new Date());

        this.paymentTransactionMapper.insert(paymentTransaction);
        return paymentTransaction;
    }

    @Transactional("db")
    public void createPaymentTransaction(PaymentOrder paymentOrder, List<PaymentOrderItem> paymentOrderItems, PaymentTransaction paymentTransaction) {
        this.processTotalAmount(paymentOrder, paymentOrderItems);
        paymentOrder.setCreateTime(new Date());
        paymentOrder.setCreateDate(new Date());
        this.paymentOrderMapper.insert(paymentOrder);

        for (PaymentOrderItem paymentOrderItem : paymentOrderItems) {
            paymentOrderItem.setPaymentOrderId(paymentOrder.getId());
            paymentOrderItemMapper.insert(paymentOrderItem);
        }

        paymentTransaction.setPaymentOrderId(paymentOrder.getId());
        paymentTransaction.setPaymentAmount(paymentOrder.getTotalPremium());
        //创建的是支付的交易
        paymentTransaction.setInterfaceType(PaymentInterfaceType.PAY);
        paymentTransaction.setCreateDate(new Date());

        this.paymentTransactionMapper.insert(paymentTransaction);
    }

    /**
     * 计算总保额和总保费
     *
     * @param paymentOrder
     * @param paymentOrderItems
     */
    private void processTotalAmount(PaymentOrder paymentOrder, List<PaymentOrderItem> paymentOrderItems) {
        BigDecimal totalInsuredAmount = BigDecimal.ZERO;
        BigDecimal totalPremium = BigDecimal.ZERO;

        for (PaymentOrderItem paymentOrderItem : paymentOrderItems) {
            totalPremium = totalPremium.add(paymentOrderItem.getPremium());
        }
        paymentOrder.setTotalInsuredAmount(totalInsuredAmount);
        paymentOrder.setTotalPremium(totalPremium);
    }

    //特指交易类型为（支付）的交易
    public List<PaymentTransaction> queryPaymentTransaction(String orderNumber, String applicantNumber, PaymentTransactionStatus status, Long channelId, Date startDate, Date endDate,
                                                            PageInfo pageInfo) {
        return this.paymentTransactionMapper.queryPaymentTransaction(orderNumber, applicantNumber, status, PaymentInterfaceType.PAY, channelId, startDate, endDate, pageInfo);
    }

    public ServiceResult<List<PaymentTransaction>> queryPaymentTransactionRPC(String orderNumber, String applicantNumber, PaymentTransactionStatus status, Long channelId, Date startDate, Date endDate,
                                                                              PageInfo pageInfo) {

        ServiceResult<List<PaymentTransaction>> result = new ServiceResult<List<PaymentTransaction>>();
        result.setData(this.queryPaymentTransaction(orderNumber, applicantNumber, status, channelId, startDate, endDate, pageInfo));
        result.setPageInfo(pageInfo);
        return result;
    }

    public PaymentOrder queryPaymentOrderGroup(String orderNumber) {
        PaymentOrder paymentOrder = paymentOrderMapper.selectByOrderNumber(orderNumber);
        List<PaymentOrderItem> orderItem = paymentOrderItemMapper.selectByPaymentOrderId(paymentOrder.getId());
        List<PaymentTransaction> paymentTransactions = paymentTransactionMapper.selectByPaymentOrderId(paymentOrder.getId());
        paymentOrder.setPaymentOrderItems(orderItem);
        paymentOrder.setPaymentTransactions(paymentTransactions);
        return paymentOrder;
    }


    public ServiceResult<PaymentTransaction> getPaymentTransactionByInternalTransactionNumber(String internalTransactionNumber) {
        PaymentTransaction data = this.paymentTransactionMapper.selectByInterTradeNo(internalTransactionNumber);
        return ServiceResult.<PaymentTransaction>builder().success(true).data(data).build();
    }

    /**
     * 支付成功，订单业务回调处理程序
     * 1.判断该笔订单是否已经做过处理
     * 如果没有做过处理，根据订单号internalTransactionNumber在订单系统中查到该笔订单的详细，并执行业务程序
     * 判断请求时的total_fee、seller_id与通知时获取的total_fee、seller_id为一致的
     * 如果有做过处理，不执行业务程序
     * @param internalTransactionNumber 内部交易号
     * @param externalTransactionNumber 外部交易流水号
     * @return
     */
    @Transactional("db")
    public ServiceResult<PaymentTransaction> paymentConfirm(String internalTransactionNumber, String externalTransactionNumber, String seller_id, BigDecimal total_fee) {

        PaymentTransaction paymentTransaction = this.paymentTransactionMapper.selectByInterTradeNo(internalTransactionNumber);

        if (paymentTransaction == null) {
            logger.error("没有找到该条交易记录internalTransactionNumber={}", internalTransactionNumber);
            return ServiceResult.<PaymentTransaction>builder().success(false).message("没有找到该条交易记录internalTransactionNumber=" + internalTransactionNumber).build();
        }
        if (externalTransactionNumber.equals(paymentTransaction.getExternalTransactionNumber())) {
            logger.error("重复回调internalTransactionNumber={}", internalTransactionNumber);
            return ServiceResult.<PaymentTransaction>builder().success(false).message("重复回调internalTransactionNumber=" + internalTransactionNumber).build();
        }
        //校验seller_id
        String merchId = paymentChannelMapper.selectByPrimaryKey(paymentTransaction.getPaymentChannelId()).getMerchantCode();
        if (!merchId.equals(seller_id)) {
            logger.error("商户号不一致seller_id={}，MerchantCode={}", seller_id, merchId);
            return ServiceResult.<PaymentTransaction>builder().success(false).message("商户号不一致seller_id=" + seller_id + ", MerchantCode=" + merchId).build();
        }
        // 校验total_fee
        BigDecimal paymentAmount = paymentTransaction.getPaymentAmount();
        if (paymentAmount.compareTo(total_fee) != 0) {
            logger.error("订单金额不一致total_fee={}，paymentAmount={}", total_fee, paymentAmount);
            return ServiceResult.<PaymentTransaction>builder().success(false).message("订单金额不一致total_fee=" + seller_id + "，paymentAmount=" + paymentAmount).build();
        }
        //更新transaction状态
        paymentTransaction.setExternalTransactionNumber(externalTransactionNumber);
        paymentTransaction.setPaymentStatus(PaymentTransactionStatus.SUCCESS);
        this.paymentTransactionMapper.updateByPrimaryKeySelective(paymentTransaction);

        //更新order状态
        PaymentOrder paymentOrder = this.paymentOrderMapper.selectByPrimaryKey(paymentTransaction.getPaymentOrderId());
        paymentOrder.setStatus(PaymentOrderPayStatus.PAID);
        //设置为未对账
        paymentOrder.setCheckStatus(PaymentOrderCheckStatus.NOT_CONFIRM);
        paymentOrderMapper.updateByPrimaryKeySelective(paymentOrder);

        return ServiceResult.<PaymentTransaction>builder().success(true).data(paymentTransaction).build();
    }

    /**
     * 支付失败，更新订单和交易状态
     *
     * @param internalTransactionNumber
     * @param externalTransactionNumber
     * @return
     */
    public ServiceResult<PaymentTransaction> setFailStatus(String internalTransactionNumber, String externalTransactionNumber) {
        PaymentTransaction paymentTransaction = this.paymentTransactionMapper.selectByInterTradeNo(internalTransactionNumber);

        if (paymentTransaction == null) {
            logger.error("没有找到该条交易记录internalTransactionNumber={}", internalTransactionNumber);
            return ServiceResult.<PaymentTransaction>builder().success(false).message("没有找到该条交易记录internalTransactionNumber=" + internalTransactionNumber).build();
        }
        if (externalTransactionNumber.equals(paymentTransaction.getExternalTransactionNumber())) {
            logger.error("重复回调internalTransactionNumber={}", internalTransactionNumber);
            return ServiceResult.<PaymentTransaction>builder().success(false).message("重复回调internalTransactionNumber=" + internalTransactionNumber).build();
        }
        //更新transaction状态
        paymentTransaction.setExternalTransactionNumber(externalTransactionNumber);
        paymentTransaction.setPaymentStatus(PaymentTransactionStatus.FAILED);
        this.paymentTransactionMapper.updateByPrimaryKeySelective(paymentTransaction);

        //更新order状态
        PaymentOrder paymentOrder = this.paymentOrderMapper.selectByPrimaryKey(paymentTransaction.getPaymentOrderId());
        paymentOrder.setStatus(PaymentOrderPayStatus.PAYERROR);
        //设置为未对账
        paymentOrder.setCheckStatus(PaymentOrderCheckStatus.NOT_CONFIRM);
        paymentOrderMapper.updateByPrimaryKeySelective(paymentOrder);

        return ServiceResult.<PaymentTransaction>builder().success(false).data(paymentTransaction).build();
    }

    @Transactional("db")
    public ServiceResult<PaymentTransaction> refundConfirm(String internalTransactionNumber, String externalTransactionNumber) {
        //TODO 完成退款的回调处理同步更新订单，交易，退款申请
        PaymentTransaction paymentTransaction = paymentTransactionMapper.selectByInterTradeNo(internalTransactionNumber);
        paymentTransaction.setExternalTransactionNumber(externalTransactionNumber);
        paymentTransaction.setPaymentStatus(PaymentTransactionStatus.SUCCESS);

        PaymentOrder paymentOrder = this.paymentOrderMapper.selectByPrimaryKey(paymentTransaction.getPaymentOrderId());
        RefundApplication refundApplication = refundApplicationMapper.selectByBusinessNumber(paymentOrder.getOrderNumber(), paymentTransaction.getInternalTransactionNumber());

        //更新订单退款金额
        BigDecimal totalRefundAmount = paymentOrder.getRefundAmount();
        if (totalRefundAmount != null) {
            paymentOrder.setRefundAmount(totalRefundAmount.add(refundApplication.getRefundAmount()));
        } else {
            paymentOrder.setRefundAmount(refundApplication.getRefundAmount());
        }

        //更新订单退款状态
        if (paymentOrder.getRefundAmount().compareTo(paymentOrder.getTotalPremium()) == 0) {
            paymentOrder.setRefundStatus(PaymentOrderRefundStatus.FULL_REFUND);
        } else {
            paymentOrder.setRefundStatus(PaymentOrderRefundStatus.PART_REFUND);
        }

        //更新退款申请状态
        refundApplication.setStatus(RefundApplicationStatus.SUCCESS);
        refundApplication.setResponse(RefundApplicationStatus.SUCCESS.getDescription());

        paymentOrderMapper.updateByPrimaryKey(paymentOrder);
        refundApplicationMapper.updateByPrimaryKey(refundApplication);
        paymentTransactionMapper.updateByPrimaryKeySelective(paymentTransaction);

        return ServiceResult.<PaymentTransaction>builder().success(true).data(null).build();
    }

    /**
     * 同步退款结果处理
     */
    @Transactional("db")
    public void synchronizedRefundConfirm(RefundApplication refundApplication, PaymentTransaction paymentTransaction) {

        PaymentOrder paymentOrder = this.paymentOrderMapper.selectByPrimaryKey(paymentTransaction.getPaymentOrderId());
        //退款处理中，更新order状态为处理中
        if (RefundApplicationStatus.PROCESSING.equals(refundApplication.getStatus())) {
            paymentOrder.setRefundStatus(PaymentOrderRefundStatus.PROCESSING_REFUND);
        } else if (RefundApplicationStatus.SUCCESS.equals(refundApplication.getStatus()) || RefundApplicationStatus.FAILED.equals(refundApplication.getStatus())) {
            //更新订单退款金额
            BigDecimal totalRefundAmount = paymentOrder.getRefundAmount();
            if (totalRefundAmount != null) {
                paymentOrder.setRefundAmount(totalRefundAmount.add(refundApplication.getRefundAmount()));
            } else {
                paymentOrder.setRefundAmount(refundApplication.getRefundAmount());
            }

            //更新订单退款状态
            if (paymentOrder.getRefundAmount().compareTo(paymentOrder.getTotalPremium()) == 0) {
                paymentOrder.setRefundStatus(PaymentOrderRefundStatus.FULL_REFUND);
            } else {
                paymentOrder.setRefundStatus(PaymentOrderRefundStatus.PART_REFUND);
            }
        }

        paymentOrderMapper.updateByPrimaryKey(paymentOrder);
        refundApplicationMapper.updateByPrimaryKey(refundApplication);
        paymentTransactionMapper.updateByPrimaryKeySelective(paymentTransaction);

    }

    @Transactional("db")
    public void outOfTime(String orderNumber) {
        paymentOrderMapper.updateOrderStatusToClose(orderNumber);
        paymentTransactionMapper.updateTransactionStatusToClose(orderNumber);
    }

}
