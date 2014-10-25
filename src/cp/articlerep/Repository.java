package cp.articlerep;

import java.util.HashSet;

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
		this.byAuthor = new HashTable<String, List<Article>>(nkeys*2);
		this.byKeyword = new HashTable<String, List<Article>>(nkeys*2);
		this.byArticleId = new HashTable<Integer, Article>(nkeys*2);
	}

	//TODO meti locks aqui, confirmar se é assim
	public boolean insertArticle(Article a) {

		if (byArticleId.contains(a.getId()))
			return false;

		Iterator<String> authors = a.getAuthors().iterator();
		while (authors.hasNext()) {
			String name = authors.next();
				
			List<Article> ll = byAuthor.get(name);
			
			if (ll == null) {
				ll = new LinkedList<Article>();
				byAuthor.getWriteLock().lock();
				byAuthor.put(name, ll);
				byAuthor.getWriteLock().unlock();
			}
			ll.getWriteLock().lock();
			ll.add(a);
			ll.getWriteLock().unlock();
		}

		Iterator<String> keywords = a.getKeywords().iterator();
		while (keywords.hasNext()) {
			String keyword = keywords.next();

			List<Article> ll = byKeyword.get(keyword);
			if (ll == null) {
				ll = new LinkedList<Article>();
				byKeyword.getWriteLock().lock();
				byKeyword.put(keyword, ll);
				byKeyword.getWriteLock().unlock();
			} 
			ll.getWriteLock().lock();
			ll.add(a);
			ll.getWriteLock().unlock();
		}

		byArticleId.getWriteLock().lock();
		byArticleId.put(a.getId(), a);
		byArticleId.getWriteLock().unlock();

		return true;
	}

	//TODO
	public void removeArticle(int id) {
		byArticleId.getReadLock().lock();
		Article a = byArticleId.get(id);

		if (a == null)
			return;
		byArticleId.getReadLock().unlock();
		
		byArticleId.getWriteLock().lock();
		byArticleId.remove(id);
		byArticleId.getWriteLock().unlock();

		Iterator<String> keywords = a.getKeywords().iterator();
		while (keywords.hasNext()) {
			String keyword = keywords.next();

			byKeyword.getReadLock().lock();
			List<Article> ll = byKeyword.get(keyword);
			byKeyword.getReadLock().unlock();
			
			if (ll != null) {
				ll.getWriteLock().lock();
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
				ll.getWriteLock().unlock();
				
				it = ll.iterator();
				if (!it.hasNext()) { // checks if the list is empty
					byKeyword.getWriteLock().lock();
					byKeyword.remove(keyword);
					byKeyword.getWriteLock().unlock();
				}
			}
		}

		Iterator<String> authors = a.getAuthors().iterator();
		while (authors.hasNext()) {
			String name = authors.next();
			
			byAuthor.getReadLock().lock();
			List<Article> ll = byAuthor.get(name);
			byAuthor.getReadLock().unlock();
			
			if (ll != null) {
				ll.getWriteLock().lock();
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
				ll.getWriteLock().unlock();
				it = ll.iterator(); 
				if (!it.hasNext()) { // checks if the list is empty
					byAuthor.getWriteLock().lock();
					byAuthor.remove(name);
					byAuthor.getWriteLock().unlock();
				}
			}
		}
	}

	public List<Article> findArticleByAuthor(List<String> authors) {
		List<Article> res = new LinkedList<Article>();

		Iterator<String> it = authors.iterator();
		while (it.hasNext()) {
			String name = it.next();
			byAuthor.getReadLock().lock();
			List<Article> as = byAuthor.get(name);
			byAuthor.getReadLock().unlock();
			if (as != null) {
				Iterator<Article> ait = as.iterator();
				while (ait.hasNext()) {
					Article a = ait.next();
					res.getWriteLock().lock();
					res.add(a);
					res.getWriteLock().unlock();
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
			byKeyword.getReadLock().lock();
			List<Article> as = byKeyword.get(keyword);
			byKeyword.getReadLock().unlock();
			if (as != null) {
				Iterator<Article> ait = as.iterator();
				while (ait.hasNext()) {
					Article a = ait.next();
					res.getWriteLock().lock();
					res.add(a);
					res.getWriteLock().unlock();
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
	//TODO
	public boolean validate() {
		
		HashSet<Integer> articleIds = new HashSet<Integer>();
		int articleCount = 0;
		
		byArticleId.getReadLock().lock();
		Iterator<Article> aIt = byArticleId.values();
		byArticleId.getReadLock().unlock();
		while(aIt.hasNext()) {
			Article a = aIt.next();
			
			articleIds.add(a.getId());
			articleCount++;
			
			// check the authors consistency
			Iterator<String> authIt = a.getAuthors().iterator();
			while(authIt.hasNext()) {
				String name = authIt.next();
				if (!searchAuthorArticle(a, name)) {
					System.out.println("1");
					return false;
				}
			}
			
			// check the keywords consistency
			Iterator<String> keyIt = a.getKeywords().iterator();
			while(keyIt.hasNext()) {
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
		byAuthor.getReadLock().lock();
		List<Article> ll = byAuthor.get(author);
		byAuthor.getReadLock().unlock();
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
		byKeyword.getReadLock().lock();
		List<Article> ll = byKeyword.get(keyword);
		byKeyword.getReadLock().unlock();
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
