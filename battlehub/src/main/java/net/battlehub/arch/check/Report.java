package net.battlehub.arch.check;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The outcome of a validation: problems found (errors fail a generation, warnings are reported) and metrics. */
public final class Report {
	public enum Level {
		ERROR, WARNING
	}

	public record Issue(Level level, String code, String message) {
		@Override
		public String toString() {
			return level + " " + code + ": " + message;
		}
	}

	public final List<Issue> issues = new ArrayList<>();
	public final Map<String, Double> metrics = new LinkedHashMap<>();

	public void error(String code, String message) {
		issues.add(new Issue(Level.ERROR, code, message));
	}

	public void warn(String code, String message) {
		issues.add(new Issue(Level.WARNING, code, message));
	}

	public void metric(String name, double value) {
		metrics.put(name, value);
	}

	public boolean ok() {
		for (Issue i : issues) {
			if (i.level() == Level.ERROR) return false;
		}
		return true;
	}

	public List<Issue> errors() {
		List<Issue> out = new ArrayList<>();
		for (Issue i : issues) {
			if (i.level() == Level.ERROR) out.add(i);
		}
		return out;
	}

	public long count(String code) {
		return issues.stream().filter(i -> i.code().equals(code)).count();
	}

	public void addAll(Report other) {
		issues.addAll(other.issues);
		metrics.putAll(other.metrics);
	}

	@Override
	public String toString() {
		return (ok() ? "ok" : "FAILED") + " " + issues.size() + " issues " + metrics;
	}
}
