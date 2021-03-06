package test.com.jd.binary.protocal.perf;

import java.io.Serializable;

public class PeopleData implements People , Serializable{

	private static final long serialVersionUID = -2129699860867079941L;
	
	private int id;
	private String name;
	private int age;

	private Location homeAddress;

	public PeopleData() {
	}

	public PeopleData(int id, String name, int age, Location homeAddress) {
		this.id = id;
		this.name = name;
		this.age = age;
		this.homeAddress= homeAddress;
	}

	@Override
	public int getId() {
		return id;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public int getAge() {
		return age;
	}
	
	@Override
	public Location getHomeAddress() {
		return homeAddress;
	}
}
