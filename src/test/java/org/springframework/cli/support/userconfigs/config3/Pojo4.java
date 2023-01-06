package org.springframework.cli.support.userconfigs.config3;

import org.springframework.cli.support.userconfigs.UserConfigs;

@UserConfigs(partition = "p1", space = "", version = "1", field = "version")
public class Pojo4 {

	String field1;

	public String getField1() {
		return field1;
	}

	public void setField1(String field1) {
		this.field1 = field1;
	}

}
