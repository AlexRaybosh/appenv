package appenv.task;

public class TaskRecord {
	final Integer taskTypeId;
	final String ticket;
	private long id;
	private Long processAtMs;
	private byte[] payload;
	public TaskRecord(Integer type, String ticket) {this.taskTypeId=type; this.ticket=ticket;}
	final public String getTicket() {
		return ticket;
	}
	final public Integer getTaskTypeId() {
		return taskTypeId;
	}
	public void setId(long id) {
		this.id=id;
	}
	public Long getId() {
		return id;
	}
	public long getProcessAtMs() {
		return processAtMs==null?System.currentTimeMillis():processAtMs;
	}
	public byte[] getPayload() {
		return payload;
	}
	

}
