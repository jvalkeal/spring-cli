package org.springframework.cli.support.xxx.config1;

import org.springframework.cli.support.xxx.SettingsBinding;

@SettingsBinding(space = "root1")
public class Pojo1 {

	String field1;

	public Pojo1() {
	}

	public Pojo1(String field1) {
		this.field1 = field1;
	}

	public static Pojo1 of(String field1) {
		return new Pojo1(field1);
	}

	public String getField1() {
		return field1;
	}

	public void setField1(String field1) {
		this.field1 = field1;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((field1 == null) ? 0 : field1.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		Pojo1 other = (Pojo1) obj;
		if (field1 == null) {
			if (other.field1 != null) {
				return false;
			}
		}
		else if (!field1.equals(other.field1)) {
			return false;
		}
		return true;
	}
}
