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

	public boolean insertArticle(Article a) {

		ReentrantReadWriteLock aLock = byArticleId.getLock(a.getId());
		ReentrantReadWriteLock auLock;
		ReentrantReadWriteLock kLock;

		aLock.readLock().lock();

		if (byArticleId.contains(a.getId())) {
			aLock.readLock().unlock();
			return false;
		}
		aLock.readLock().unlock();
		aLock.writeLock().lock();

		Iterator<String> authors = a.getAuthors().iterator();
		while (authors.hasNext()) {
			String name = authors.next();

			// TODO
			// Faz lock a cada autor
			// Tenho de fazer lock a todos ao mesmo tempo? ou basta 1 a 1?
			auLock = byAuthor.getLock(name);
			auLock.writeLock().lock();

			List<Article> ll = byAuthor.get(name);

			if (ll == null) {
				ll = new LinkedList<Article>();
				byAuthor.put(name, ll);
			}
			ll.add(a);
			auLock.writeLock().unlock();
		}

		Iterator<String> keywords = a.getKeywords().iterator();
		while (keywords.hasNext()) {
			String keyword = keywords.next();

			// TODO
			// Faz lock a cada autor
			// Tenho de fazer lock a todos ao mesmo tempo? ou basta 1 a 1?
			kLock = byKeyword.getLock(keyword);
			kLock.writeLock().lock();

			List<Article> ll = byKeyword.get(keyword);
			if (ll == null) {
				ll = new LinkedList<Article>();
				byKeyword.put(keyword, ll);
			}
			ll.add(a);
			kLock.writeLock().unlock();
		}

		byArticleId.put(a.getId(), a);

		aLock.writeLock().unlock();

		return true;
	}

	public void removeArticle(int id) {
		ReentrantReadWriteLock aLock = byArticleId.getLock(id);
		ReentrantReadWriteLock auLock;
		ReentrantReadWriteLock kLock;

		// read lock para fazer get
		aLock.readLock().lock();
		Article a = byArticleId.get(id);

		if (a == null) {
			aLock.readLock().unlock();
			return;
		}
		aLock.readLock().unlock();

		// TODO write lock para poder eliminar
		aLock.writeLock().lock();
		byArticleId.remove(id);
		aLock.writeLock().unlock();

		Iterator<String> keywords = a.getKeywords().iterator();
		while (keywords.hasNext()) {
			String keyword = keywords.next();

			// TODO
			kLock = byKeyword.getLock(keyword);
			kLock.writeLock().lock();

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
				kLock.writeLock().unlock();
			}
		}

		Iterator<String> authors = a.getAuthors().iterator();
		while (authors.hasNext()) {
			String name = authors.next();

			// TODO
			auLock = byAuthor.getLock(name);
			auLock.writeLock().lock();

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
				auLock.writeLock().unlock();
			}
		}
	}

	public List<Article> findArticleByAuthor(List<String> authors) {
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

		return res;
	}

	public List<Article> findArticleByKeyword(List<String> keywords) {
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
