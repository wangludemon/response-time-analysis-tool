package com.demo.tool.responsetimeanalysis.entity;

import java.text.DecimalFormat;
import java.util.ArrayList;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class  SporadicTask {
	@JsonProperty("priority")
	public int priority;
	@JsonProperty("period")
	public long period;//周期
	@JsonProperty("deadline")
	public long deadline;//截止期限
	@JsonProperty("WCET_LO")
	public long C_LOW;
	@JsonProperty("WCET_HI")
	public long C_HIGH;
	@JsonProperty("partition")
	public int partition;//分区
	@JsonProperty("critical")
	public int critical;	//关键级 0：低关键 1：高关键
	@JsonProperty("id")
	public int id;
	//for allocation
	public double util;
	public double util_LOW;
	public double util_HIGH;
	public ArrayList<Integer> resource_required_index;
	public ArrayList<Integer> number_of_access_in_one_release;	// 一次release的资源访问数量
	public ArrayList<Integer> resource_required_priority;
	public long prec_LOW = 0;	// 关键性切换 LO->HI
	public long prec_HIGH = 0;	//关键性切换
	public long WCET = 0;
	public long pure_resource_execution_time = 0;	//执行资源的时间
	public long Ri = 0, spin = 0, interference = 0, local = 0, total_blocking = 0, indirect_spin = 0;	//interference 是高优先级任务的WCET+spin,indirect spin是高优先级任务的spin
	public long Ri_HI = 0, Ri_LO = 0, Ri_Switch = 0;
	public long PWLP_S= 0;
	public long spin_delay_by_preemptions = 0; //PWLP重试成本
	public double implementation_overheads = 0, blocking_overheads = 0;
	//删
	public double mrsp_arrivalblocking_overheads = 0, fifonp_arrivalblocking_overheads = 0, fifop_arrivalblocking_overheads = 0;
	public double migration_overheads_plus = 0;
	public double np_section, test_delay = 0;
	/* Used by LP solver from C code */
	public int hasResource = 0;
	public int[] resource_required_index_copy = null;
	public int[] number_of_access_in_one_release_copy = null;
	public int schedulable = -1 ;

	// 定义一个内部方法来处理 JSON 数组
	@JsonProperty("resource_requests")
	private void unpackResourceRequests(ArrayList<ResourceRequest> resourceRequests) {
		for (ResourceRequest request : resourceRequests) {
			this.resource_required_index.add(request.getResourceId());
			this.number_of_access_in_one_release.add(request.getRequestNumber());
		}
	}

	// ResourceRequest内部类来临时存储请求数据
	private static class ResourceRequest {
		@JsonProperty("resource_id")
		private int resourceId;
		@JsonProperty("request_number")
		private int requestNumber;
		// 无参构造器
		public ResourceRequest() {
		}
		public int getResourceId() {
			return resourceId;
		}

		public void setResourceId(int resourceId) {
			this.resourceId = resourceId;
		}

		public int getRequestNumber() {
			return requestNumber;
		}

		public void setRequestNumber(int requestNumber) {
			this.requestNumber = requestNumber;
		}
	}

	@JsonCreator
	public SporadicTask(
			@JsonProperty("priority") int priority,
			@JsonProperty("period") long t,
			@JsonProperty("deadline") long d,
			@JsonProperty("WCET_LO") long clo,
			@JsonProperty("WCET_HI") long chi,
			@JsonProperty("partition") int partition,
			@JsonProperty("id") int id,
			@JsonProperty("critical") int critical) {
		this.priority = priority;
		this.period = t;
		this.C_LOW = clo;
		this.deadline = d;
		this.partition = partition;
		this.id = id;
		this.critical = critical;

		if (critical == 0) {
			this.C_HIGH = 0;
			this.util_HIGH = 0;

		} else {
			this.C_HIGH = chi;
			this.util = util_HIGH;
		}

		this.resource_required_index = new ArrayList<>();
		this.number_of_access_in_one_release = new ArrayList<>();
		this.resource_required_priority = new ArrayList<>();
	}


	public SporadicTask(int priority, long t, long clo,  int partition, int id,  int critical) {
		this(priority, t, clo,  partition, id, (double)-1, critical, 2);
	}

	public SporadicTask(int priority, long t, long clo, int partition, int id, double util_LOW, int critical, double CF) {
		this.priority = priority;
		this.period = t;
		this.C_LOW = clo;
		this.deadline = t;
		this.partition = partition;
		this.id = id;

		this.critical = critical;
		this.util_LOW = util_LOW;

		if(critical==0){
			this.C_HIGH = 0;
			this.util_HIGH = 0;
			this.util = util_LOW;
		}else{
			C_HIGH =  (long)(clo*CF);
			this.util_HIGH = util_LOW*CF;
			this.util = util_HIGH;
		}

		resource_required_index = new ArrayList<>();
		number_of_access_in_one_release = new ArrayList<>();

		Ri = 0;
		spin = 0;
		interference = 0;
		local = 0;
		Ri_HI = 0;Ri_LO = 0;Ri_Switch = 0;
	}

	@Override
	public String toString() {
		return "T" + this.id + " : T = " + this.period +
				", C_LOW = " + this.C_LOW + ", C_HIGH = " + this.C_HIGH+
				", PRET_LOW: " + this.prec_LOW + ", D = " + this.deadline
				+ ", Priority = " + this.priority + ", Partition = " + this.partition;
	}

	public String RTA() {
		return "T" + this.id + " : R = " + this.Ri + ", S = " + this.spin + ", I = " + this.interference + ", A = " + this.local + ". is schedulable: "
				+ (Ri <= deadline);
	}
	public String Ris(){
		return "Ri_Low= " + this.Ri_LO +" Ri_High = " + this.Ri_HI + " Ri_Swi= "+this.Ri_Switch+ " D: "+this.deadline;
	}

	public String getInfo() {
		System.out.println(this.resource_required_index.toString());
		System.out.println(this.number_of_access_in_one_release.toString());
		DecimalFormat df = new DecimalFormat("#.#######");
		return "T" + this.id + " : T = " + this.period + ", C_LOW = " + this.C_LOW + ", C_HIGH = " + this.C_HIGH + ", PRET: " + this.pure_resource_execution_time + ", D = " + this.deadline
				+ ", Priority = " + this.priority + ", Partition = " + this.partition + ", Util_LOW: " + Double.parseDouble(df.format(this.util_LOW));
	}

	// no access, return -1
	public int getWaittingPriority(Resource resource){
		//public ArrayList<Integer> resource_required_index;
		//public ArrayList<Integer> resource_required_priority;
		int index = resource_required_index.indexOf(resource.id-1);
		if (index == -1){
			return -1;
		}
		return resource_required_priority.get(index);

	}
}
