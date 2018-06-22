package com.dma.web;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Tools {

	public final static String toJSON(Object o) throws IOException{
		ObjectMapper mapper = new ObjectMapper();
		mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
		StringWriter sw = new StringWriter();
		String jsonResult = null;
		mapper.writeValue(sw, o);
		sw.flush();
		jsonResult = sw.toString();
		sw.close();
		return jsonResult;
	}
	
	public final static Map<String, Object> fromJSON(InputStream is) throws IOException {
		Map<String, Object>	map = new HashMap<String, Object>();
		
		InputStreamReader isr = new InputStreamReader(is);
		BufferedReader br = new BufferedReader(isr);
        ObjectMapper mapper = new ObjectMapper();
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
		map = mapper.readValue(br, new TypeReference<Map<String, Object>>(){});
		
        return map;
	}

	
}
