package org.springframework.cli.support.userconfigs.config2;

import org.springframework.cli.support.userconfigs.UserConfigs;

@UserConfigs(space = "", version = "1", versions = { "1", "2" })
public class Pojo3 {

	String field1;

	public Pojo3() {
	}

	public Pojo3(String field1) {
		this.field1 = field1;
	}

	public static Pojo3 of(String field1) {
		return new Pojo3(field1);
	}

	public String getField1() {
		return field1;
	}

	public void setField1(String field1) {
		this.field1 = field1;
	}

	// @SettingsMigration
	// void from() {
	// }

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
		Pojo3 other = (Pojo3) obj;
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
