package com.batch.processor;

import org.springframework.batch.item.ItemProcessor;

import com.batch.model.UserVO;


public class UserItemProcessor implements ItemProcessor<UserVO, UserVO>{

	@Override
	public UserVO process(UserVO item) throws Exception {
		
		return item;
	}

}
