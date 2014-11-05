package cp.articlerep;

import java.util.HashSet;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import cp.articlerep.ds.Iterator;
import cp.articlerep.ds.LinkedList;
import cp.articlerep.ds.List;
import cp.articlerep.ds.Map;
import cp.articlerep.ds.HashTable;

/**
 * @author Ricardo Dias
 */
public class Repository {

	private Map<String, List<Article>> byAuthor;
	private Map<String, List<Article>> byKeyword;
	private Map<Integer, Article> byArticleId;

	public Repository(int nkeys) {
		this.byAuthor = new HashTable<String, List<Article>>(nkeys * 2);
		this.byKeyword = new HashTable<String, List<Article>>(nkeys * 2);
		this.byArticleId = new HashTable<Integer, Article>(nkeys * 2);
	}

	/**
	 * Metodo que faz lock a uma lista de Locks
	 * 
	 * @param locks
	 *            - Lista de locks
	 * @param write
	 *            - variavel boleana que define se vai ser um writeLock ou
	 *            readLock, writeLock = true, readLock = false
	 * 
	 */
	private void lockList(List<ReentrantReadWriteLock> locks, boolean write) {
		Iterator<ReentrantReadWriteLock> locksI = locks.iterator();
		while (locksI.hasNext()) {
			ReentrantReadWriteLock lock = locksI.next();
			if (write)
				lock.writeLock().lock();
			else {
				lock.readLock().lock();
			}

		}
	}

	/**
	 * Metodo que faz unlock a uma lista de Locks
	 * 
	 * @param locks
	 *            - Lista de locks
	 * @param write
	 *            - variavel boleana que define se vai ser um writeLock ou
	 *            readLock, writeLock = true, readLock = false
	 * 
	 */
	private void unlockList(List<ReentrantReadWriteLock> locks, boolean write) {
		Iterator<ReentrantReadWriteLock> locksI = locks.iterator();
		while (locksI.hasNext()) {
			ReentrantReadWriteLock lock = locksI.next();

			if (write)
				lock.writeLock().unlock();
			else {
				lock.readLock().unlock();
			}

		}
	}

	public boolean insertArticle(Article a) {

		ReentrantReadWriteLock aLock = byArticleId.getLock(a.getId());

		aLock.writeLock().lock();

		if (byArticleId.contains(a.getId())) {
			aLock.writeLock().unlock();
			return false;
		}

		/*
		 * Get de todos os locks que nos interessam
		 */
		List<ReentrantReadWriteLock> authorLocks = byAuthor.getLocks(a
				.getAuthors());
		List<ReentrantReadWriteLock> keywordLocks = byKeyword.getLocks(a
				.getKeywords());

		lockList(authorLocks, true);
		lockList(keywordLocks, true);

		Iterator<String> authors = a.getAuthors().iterator();
		while (authors.hasNext()) {
			String name = authors.next();

			List<Article> ll = byAuthor.get(name);

			if (ll == null) {
				ll = new LinkedList<Article>();
				byAuthor.put(name, ll);
			}
			ll.add(a);
		}

		Iterator<String> keywords = a.getKeywords().iterator();
		while (keywords.hasNext()) {
			String keyword = keywords.next();

			List<Article> ll = byKeyword.get(keyword);
			if (ll == null) {
				ll = new LinkedList<Article>();
				byKeyword.put(keyword, ll);
			}
			ll.add(a);
		}

		byArticleId.put(a.getId(), a);

		unlockList(authorLocks, true);
		unlockList(keywordLocks, true);

		aLock.writeLock().unlock();

		return true;
	}

	public void removeArticle(int id) {
		ReentrantReadWriteLock aLock = byArticleId.getLock(id);

		aLock.writeLock().lock();
		Article a = byArticleId.get(id);

		if (a == null) {
			aLock.writeLock().unlock();
			return;
		}

		byArticleId.remove(id);

		/*
		 * Get de todos os locks que nos interessam
		 */
		List<ReentrantReadWriteLock> authorLocks = byAuthor.getLocks(a
				.getAuthors());
		List<ReentrantReadWriteLock> keywordLocks = byKeyword.getLocks(a
				.getKeywords());

		lockList(authorLocks, true);
		lockList(keywordLocks, true);

		Iterator<String> keywords = a.getKeywords().iterator();
		while (keywords.hasNext()) {
			String keyword = keywords.next();

			List<Article> ll = byKeyword.get(keyword);

			if (ll != null) {
				int pos = 0;
				Iterator<Article> it = ll.iterator();
				while (it.hasNext()) {
					Article toRem = it.next();
					if (toRem == a) {
						break;
					}
					pos++;
				}
				ll.remove(pos);

				it = ll.iterator();
				if (!it.hasNext()) { // checks if the list is empty
					byKeyword.remove(keyword);
				}
			}
		}

		Iterator<String> authors = a.getAuthors().iterator();
		while (authors.hasNext()) {
			String name = authors.next();

			List<Article> ll = byAuthor.get(name);

			if (ll != null) {
				int pos = 0;
				Iterator<Article> it = ll.iterator();
				while (it.hasNext()) {
					Article toRem = it.next();
					if (toRem == a) {
						break;
					}
					pos++;
				}
				ll.remove(pos);
				it = ll.iterator();
				if (!it.hasNext()) { // checks if the list is empty
					byAuthor.remove(name);
				}
			}
		}
		unlockList(authorLocks, true);
		unlockList(keywordLocks, true);

		/*
		 * Unlock no final do metodo para o programa nao retornar leituras
		 * erradas
		 */
		aLock.writeLock().unlock();
	}

	public List<Article> findArticleByAuthor(List<String> authors) {

		List<ReentrantReadWriteLock> authorLocks = byAuthor.getLocks(authors);
		lockList(authorLocks, false);

		List<Article> res = new LinkedList<Article>();

		Iterator<String> it = authors.iterator();
		while (it.hasNext()) {
			String name = it.next();

			List<Article> as = byAuthor.get(name);
			if (as != null) {
				Iterator<Article> ait = as.iterator();
				while (ait.hasNext()) {
					Article a = ait.next();
					res.add(a);
				}
			}
		}
		unlockList(authorLocks, false);
		return res;
	}

	public List<Article> findArticleByKeyword(List<String> keywords) {

		List<ReentrantReadWriteLock> keywordLocks = byAuthor.getLocks(keywords);
		lockList(keywordLocks, false);

		List<Article> res = new LinkedList<Article>();

		Iterator<String> it = keywords.iterator();
		while (it.hasNext()) {
			String keyword = it.next();

			List<Article> as = byKeyword.get(keyword);
			if (as != null) {
				Iterator<Article> ait = as.iterator();
				while (ait.hasNext()) {
					Article a = ait.next();
					res.add(a);
				}
			}

		}
		unlockList(keywordLocks, false);
		return res;
	}

	/**
	 * This method is supposed to be executed with no concurrent thread
	 * accessing the repository.
	 * 
	 */
	public boolean validate() {

		HashSet<Integer> articleIds = new HashSet<Integer>();
		int articleCount = 0;

		Iterator<Article> aIt = byArticleId.values();
		while (aIt.hasNext()) {
			Article a = aIt.next();

			articleIds.add(a.getId());
			articleCount++;

			// check the authors consistency
			Iterator<String> authIt = a.getAuthors().iterator();
			while (authIt.hasNext()) {
				String name = authIt.next();
				if (!searchAuthorArticle(a, name)) {
					System.out.println("1");
					return false;
				}
			}

			// check the keywords consistency
			Iterator<String> keyIt = a.getKeywords().iterator();
			while (keyIt.hasNext()) {
				String keyword = keyIt.next();
				if (!searchKeywordArticle(a, keyword)) {
					System.out.println("2");
					return false;
				}
			}
		}

		return articleCount == articleIds.size();
	}

	private boolean searchAuthorArticle(Article a, String author) {

		List<Article> ll = byAuthor.get(author);
		if (ll != null) {
			Iterator<Article> it = ll.iterator();
			while (it.hasNext()) {
				if (it.next() == a) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean searchKeywordArticle(Article a, String keyword) {

		List<Article> ll = byKeyword.get(keyword);
		if (ll != null) {
			Iterator<Article> it = ll.iterator();
			while (it.hasNext()) {
				if (it.next() == a) {
					return true;
				}
			}
		}
		return false;
	}

}
