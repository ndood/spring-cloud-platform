package com.siebre.product.message.test.mapper.messageobject;

import java.util.Date;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.siebre.product.message.test.base.DbTestConfig;
import com.siebre.product.messagedemo.entity.MessageObject;
import com.siebre.product.messagedemo.mapper.MessageObjectMapper;

public class MessageObjectMapperTest extends DbTestConfig {
	
	@Autowired
	private MessageObjectMapper messageObjectMapper;
	
	@Test
	@Transactional
	public void insertMessageObjectTest() {
		MessageObject messageObject = new MessageObject();
		messageObject.setDescription("测试对象");
		Date currentDate = new Date();
		messageObject.setCurrentDate(currentDate);
		this.messageObjectMapper.insert(messageObject);
		
		MessageObject dbObject = this.messageObjectMapper.selectByPrimaryKey(messageObject.getId());
		
		Assert.assertTrue(messageObject.getId() == dbObject.getId());
		Assert.assertTrue(messageObject.getDescription().equals(dbObject.getDescription()));
		
	}

}
