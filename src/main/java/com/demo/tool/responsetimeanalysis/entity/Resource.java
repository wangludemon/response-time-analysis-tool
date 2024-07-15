package com.demo.tool.responsetimeanalysis.entity;

import java.util.ArrayList;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
public class Resource {

	public int id;
	public long csl;  // 临界资源长度


	public long csl_low;  //临界资源下限

	public long csl_high;  //临界资源上限

	public ArrayList<SporadicTask> requested_tasks;	//请求该资源的任务列表
	public ArrayList<Integer> partitions;	//请求该资源的分区列表
	public ArrayList<Integer> ceiling;	// 资源的静态ceiling

	public boolean onlyLow;  // 如果requested_task 里只有低关键任务时为true
	public int protocol;
	public boolean isGlobal = false;

	@JsonCreator
	public Resource(@JsonProperty("id") int id,
					@JsonProperty("csl_LO") long csl_low,
					@JsonProperty("csl_HI") long csl_high
	){
		this.id = id;
		this.csl_low = csl_low;
		this.csl = csl_low;
		this.csl_high = csl_high;
		requested_tasks = new ArrayList<>();
		partitions = new ArrayList<>();
		ceiling = new ArrayList<>();
		onlyLow = true;
	}
	public Resource(int id, long cs_len) {
		this.id = id;
		this.csl = cs_len;
		this.csl_low = cs_len;
		this.csl_high = cs_len * 2;
		requested_tasks = new ArrayList<>();
		partitions = new ArrayList<>();
		ceiling = new ArrayList<>();
		onlyLow = true;
	}

	@Override
	public String toString() {
		return "R" + this.id + " : cs len = " + this.csl_low + ", partitions: " + partitions.size() + ", tasks: " + requested_tasks.size() + ", isGlobal: "
				+ isGlobal;
	}

	public int getCeilingForProcessor(ArrayList<ArrayList<SporadicTask>> tasks, int partition) {
		int ceiling = -1;

		for (int k = 0; k < tasks.get(partition).size(); k++) {
			SporadicTask task = tasks.get(partition).get(k);

			if (task.resource_required_index.contains(this.id - 1)) {
				ceiling = task.priority > ceiling ? task.priority : ceiling;
			}
		}

		return ceiling;
	}

	public int getCeilingForProcessor(ArrayList<SporadicTask> tasks) {
		int ceiling = -1;

		for (int k = 0; k < tasks.size(); k++) {
			SporadicTask task = tasks.get(k);

			if (task.resource_required_index.contains(this.id - 1)) {
				ceiling = task.priority > ceiling ? task.priority : ceiling;
			}
		}

		return ceiling;
	}

	public int getWaitingCeilingForProcessor(ArrayList<SporadicTask> tasks) {
		int ceiling = -1;

		for (int k = 0; k < tasks.size(); k++) {
			SporadicTask task = tasks.get(k);

			if (task.resource_required_index.contains(this.id - 1)) {
				int pri = task.getWaittingPriority(this);
				ceiling = pri > ceiling ? pri : ceiling;
			}
		}


		return ceiling;
	}
}

