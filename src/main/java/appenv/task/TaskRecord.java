package appenv.task;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import appenv.env.AppEnv;
import appenv.util.JsonUtils;
import appenv.util.JsonUtils.JsonType;

public class TaskRecord {
	final TaskType taskType;
	final String ticket;
	private Long processAtMs;
	private byte[] payload;
	private Map<String,Set<String>> fieldToText;
	private Map<String,Set<Long>> fieldToNumber;
	private TaskState taskState;
	private Integer envId;
	private Long insertedAtMs;
	private int errorCount;
	
	public TaskRecord(TaskType type, String ticket, byte[] payload, Map<String,Set<String>> fieldToText, Map<String,Set<Long>> fieldToNumber) {
		this(type,ticket,payload);
		this.fieldToText=fieldToText;
		this.fieldToNumber=fieldToNumber;
	}
	public TaskRecord(TaskType type, String ticket, byte[] payload, JsonObject props) {
		this(type, ticket==null?AppEnv.createUniqueKey():ticket, props);
		this.payload=payload;
	}
	public TaskRecord(TaskType type, String ticket, JsonObject props) {
		this(type,ticket);
		if (props!=null) for (Entry<String, JsonElement> e : props.entrySet()) {
			String field=e.getKey();
			switch (JsonUtils.getType(e.getValue())) {
			case NUMBER:
				addPropNumber(field,JsonUtils.getLong(e.getValue()));
				break;
			case STRING:
				addPropString(field,JsonUtils.getString(e.getValue()));
				break;
			case ARRAY:
				addPropArray(field,e.getValue().getAsJsonArray());
				break;
			case BOOLEAN:
				addPropBool(field,JsonUtils.getBool(e.getValue()));				
			default:
				break;
			}
		}
	}
	
	private void addPropArray(String field, JsonArray arr) {
		for (JsonElement v : JsonUtils.getJsonArrayIterable(arr)) {
			if (JsonUtils.getType(v)==JsonType.NUMBER)
				addPropNumber(field,JsonUtils.getLong(v));
			else if (JsonUtils.getType(v)==JsonType.STRING)
				addPropString(field, JsonUtils.getString(v));
			else if (JsonUtils.getType(v)==JsonType.BOOLEAN)
				addPropBool(field, JsonUtils.getBool(v));			
		}
	}
	
	public void addPropString(String field, String val) {
		if (fieldToText==null) fieldToText=new HashMap<>();
		Set<String> s = fieldToText.get(field);
		if (s==null) {
			s=new HashSet<>();
			fieldToText.put(field,s);
		}
		s.add(val);
	}
	
	public void addPropNumber(String field, Long num) {
		if (fieldToNumber==null) fieldToNumber=new HashMap<>();
		Set<Long> s = fieldToNumber.get(field);
		if (s==null) {
			s=new HashSet<>();
			fieldToNumber.put(field,s);
		}
		s.add(num);		
	}
	public void addPropBool(String field, boolean b) {
		addPropString(field, b?"true":"false");
	}
	
	public TaskRecord(TaskType type, String ticket, byte[] payload) {this(type,ticket);this.payload=payload;}
	public TaskRecord(TaskType type, String ticket) {this.taskType=type; this.ticket=ticket;}
	final public String getTicket() {
		return ticket;
	}
	final public TaskType getTaskType() {
		return taskType;
	}
	final public Integer getTaskTypeId() {
		return taskType.getId();
	}

	public Long getProcessAtMs() {
		return processAtMs;
	}
	public byte[] getPayload() {
		return payload;
	}
	public Map<String,Set<String>> getFieldToText() {return fieldToText;}
	public Map<String,Set<Long>> getFieldToNumber() {return fieldToNumber;}
	public void setTaskState(TaskState taskState) {
		this.taskState=taskState;
	}
	public void setEnvId(int envId) {
		this.envId=envId;
	}
	public void setProcessAtMs(long processMs) {
		this.processAtMs=processMs;
		
	}
	public void setInsertedAtMs(long insertMs) {
		this.insertedAtMs=insertMs;
	}
	public void setPayload(byte[] payload) {
		this.payload=payload;
	}
	public void setErrorCount(int errorCount) {
		this.errorCount=errorCount;
		
	}
	public final int getErrorCount() {
		return errorCount;
	}
	public final TaskState getTaskState() {
		return taskState;
	}
	public final Integer getEnvId() {
		return envId;
	}
	public final long getInsertedAtMs() {
		return insertedAtMs;
	}
	@Override
	public String toString() {
		return "TaskRecord [taskType=" + taskType + ", ticket=" + ticket + ", processAtMs=" + processAtMs + ", payload="
				+ Arrays.toString(payload) + ", fieldToText=" + fieldToText + ", fieldToNumber=" + fieldToNumber
				+ ", taskState=" + taskState + ", envId=" + envId + "]";
	}
	@Override
	public int hashCode() {
		return Objects.hash(taskType, ticket);
	}
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		TaskRecord other = (TaskRecord) obj;
		return Objects.equals(taskType, other.taskType) && Objects.equals(ticket, other.ticket);
	}	
}
