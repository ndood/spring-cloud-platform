package com.siebre.payment.paymenttransaction.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.siebre.payment.mapper.paymenttransaction.PaymentTransactionMapper;

@Service
public class PaymentTransactionService {
	
	@Autowired
	private PaymentTransactionMapper paymentTransactionMapper;

}
