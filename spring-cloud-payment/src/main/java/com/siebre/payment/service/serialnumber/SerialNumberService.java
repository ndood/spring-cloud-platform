package com.siebre.payment.service.serialnumber;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.siebre.payment.mapper.serialnumber.SerialNumberMapper;

@Service
public class SerialNumberService {
	
	@Autowired
	private SerialNumberMapper serialNumberMapper;
	
	public String nextValue(String serialName) {
		return this.serialNumberMapper.nextValue(serialName);
	}

}
