package org.ezon.msa;

import java.util.ArrayList;
import java.util.List;

public class Pagination {
	public static final int PER_PAGE = 10;

	public static <T> List<T> paging(List<T> pagingList, int perPage, int page) throws IllegalArgumentException {
		if(pagingList.size() <= perPage) {
			return pagingList;
		}
		
		List<T> result = new ArrayList<>();
		int totalPage = totalPage(pagingList, perPage);
		if(totalPage < page) {
			throw new IllegalArgumentException("Requested page (" + page + ") exceeds max page (" + totalPage + ")");
		}
		
		int firstPage = (page - 1)*perPage;
		for(int i=0; (firstPage+i)<pagingList.size() && i < perPage; i++) {
			result.add(pagingList.get(firstPage+i));
		}
		return result;
	}
	public static int totalPage(List<?> list, int perPage) {
		int totalPage = list.size()/perPage;
		if(list.size()%perPage != 0) {
			++totalPage;
		}
		return totalPage;
	}

	
	public static <T> List<T> paging(List<T> pagingList, int page) throws IllegalArgumentException {
		return paging(pagingList, PER_PAGE, page);
	}
	
	public static int totalPage(List<?> list) {
		return totalPage(list, PER_PAGE);
	}
}
