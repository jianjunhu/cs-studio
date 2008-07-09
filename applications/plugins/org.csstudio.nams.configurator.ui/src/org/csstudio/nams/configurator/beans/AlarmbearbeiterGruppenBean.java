package org.csstudio.nams.configurator.beans;


public class AlarmbearbeiterGruppenBean extends
		AbstractConfigurationBean<AlarmbearbeiterGruppenBean> {

	public static enum PropertyNames {
		groupID, name, minGroupMember, timeOutSec, active
	}

	private int groupID;// PRIMARY KEY
	private String name;
	private short minGroupMember;
	private int timeOutSec;
	private boolean isActive;

	public AlarmbearbeiterGruppenBean() {
		groupID = -1;
	}

	public int getGroupID() {
		return groupID;
	}

	public void setGroupID(int groupID) {
		int oldValue = getGroupID();
		this.groupID = groupID;

		pcs.firePropertyChange(
				PropertyNames.groupID.name(),
				oldValue, groupID);
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		String oldValue = getName();
		this.name = name;
		pcs.firePropertyChange(
				PropertyNames.name.name(), oldValue,
				getName());
	}

	public short getMinGroupMember() {
		return minGroupMember;
	}

	public void setMinGroupMember(short minGroupMember) {
		short oldValue = getMinGroupMember();
		this.minGroupMember = minGroupMember;
		pcs.firePropertyChange(
				PropertyNames.minGroupMember.name(),
				oldValue, getMinGroupMember());
	}

	public int getTimeOutSec() {
		return timeOutSec;
	}

	public void setTimeOutSec(int timeOutSec) {
		int oldValue = getTimeOutSec();
		this.timeOutSec = timeOutSec;
		pcs.firePropertyChange(
				PropertyNames.timeOutSec.name(),
				oldValue, getTimeOutSec());
	}

	public boolean isActive() {
		return isActive;
	}

	public void setActive(boolean isActive) {
		boolean oldValue = isActive();
		this.isActive = isActive;
		pcs.firePropertyChange(
				PropertyNames.active.name(),
				oldValue, isActive());
	}

	public String getDisplayName() {
		return getName() == null ? "(ohne Name)" : getName();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + groupID;
		result = prime * result + (isActive ? 1231 : 1237);
		result = prime * result + minGroupMember;
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + timeOutSec;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		final AlarmbearbeiterGruppenBean other = (AlarmbearbeiterGruppenBean) obj;
		if (groupID != other.groupID)
			return false;
		if (isActive != other.isActive)
			return false;
		if (minGroupMember != other.minGroupMember)
			return false;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		if (timeOutSec != other.timeOutSec)
			return false;
		return true;
	}

	@Override
	protected void doUpdateState(AlarmbearbeiterGruppenBean bean) {
		setActive(bean.isActive());
		setGroupID(bean.getGroupID());
		setMinGroupMember(bean.getMinGroupMember());
		setName(bean.getName());
		setTimeOutSec(bean.getTimeOutSec());
	}

	public int getID() {
		return this.getGroupID();
	}
	
	@Override
	public String toString() {
		return getDisplayName();
	}

	public void setID(int id) {
		setGroupID(id);
	}

}
