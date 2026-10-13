package net.battlehub.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToLongFunction;

public final class Leaderboard {
	private Leaderboard() {}

	public record Row<T>(int rank, T item, long value) {}

	/** Top {@code n} by descending value; items with value {@code <= floor} are left out. Equal values share a rank. */
	public static <T> List<Row<T>> top(Collection<T> items, ToLongFunction<T> value, int n, long floor) {
		List<T> list = new ArrayList<>();
		for (T t : items) {
			if (value.applyAsLong(t) > floor) {
				list.add(t);
			}
		}
		list.sort(Comparator.comparingLong(value).reversed());
		List<Row<T>> rows = new ArrayList<>();
		long prev = Long.MIN_VALUE;
		int rank = 0;
		for (int i = 0; i < list.size() && i < n; i++) {
			long v = value.applyAsLong(list.get(i));
			if (v != prev) {
				rank = i + 1;
				prev = v;
			}
			rows.add(new Row<>(rank, list.get(i), v));
		}
		return rows;
	}
}
