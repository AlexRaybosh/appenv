package testapp;


import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import appenv.env.AppEnv;
import appenv.etc.DictionaryWord;

public class Example07_DictionaryForCommonWords {

	public static void main(String[] args) throws Exception {
		// Changed the bootstrap resource or file
		//AppEnv.presetBootstrapResource("bootstrap-07-dictionary.json");
		

		AppEnv.db().update("truncate table word_dictionary");
		
		int MAX=2000000;
		
		
		System.out.println("About to shove "+MAX+" commonly used strings");
		long start = System.currentTimeMillis();
		List<DictionaryWord> words=new LinkedList<>();
		
		//for (int z=0;z <10; ++z)
		for (int i=1;i<=MAX; ++i) {			 
			String str=String.format("word #%08d",i);
			DictionaryWord word = AppEnv.word(str);
			words.add(word);
		}
		
		long addedFirstTime = System.currentTimeMillis();
		System.out.println("Done creating "+MAX+" commonly used strings in "+(addedFirstTime-start)+" ms.");
		
		// lets ensure commit
		// once getId/getWord returns, its guaranteed to be committed to the db
		for (DictionaryWord w : words)
			w.getId(); 
		
		long committedTime = System.currentTimeMillis();
		System.out.println("Done committing "+MAX+" commonly used strings in "+(committedTime-addedFirstTime)+" ms.");
		
		AppEnv.reloadEnvironment();
		
		words.clear();
		for (int i=1;i<=MAX; ++i) {
			// we will probably need to re-read them, if they dont fit in the cache
			String str=String.format("word #%08d",i);
			DictionaryWord word = AppEnv.word(str);
			words.add(word);
		}
		long addedBack=System.currentTimeMillis();
		System.out.println("Done re-reading in " +(addedBack-committedTime)+" ms.");
		
		
		// Ensure actual DB read happened
		for (DictionaryWord w : words)
			w.getId(); 
		
		long end = System.currentTimeMillis();
		System.out.println("Done DB resolving re-reads in " +(end-addedBack)+" ms.");

		
		System.out.println("Last 10 inserted");
		for (Map<String, Object> row :AppEnv.db().selectLabelMap("select * from word_dictionary order by last_ms desc, id desc limit ?", false, 10)) {
			System.out.println("Row: "+row);
		}
		
		// We are done
		AppEnv.destroy();
		
		
		
		
	}
/*
About to shove 2000000 commonly used strings
Done creating 2000000 commonly used strings in 11996 ms.
Done committing 2000000 commonly used strings in 7815 ms.
Done re-reading in 1675 ms.
Done DB resolving re-reads in 179 ms.
Last 10 inserted
Row: {word=word #01999924, last_ms=1632267567036, id=179432572}
Row: {word=word #01999925, last_ms=1632267567036, id=179432571}
Row: {word=word #01999926, last_ms=1632267567036, id=179432570}
Row: {word=word #01999927, last_ms=1632267567036, id=179432569}
Row: {word=word #01999928, last_ms=1632267567036, id=179432568}
Row: {word=word #01999929, last_ms=1632267567036, id=179432567}
Row: {word=word #01999880, last_ms=1632267567036, id=179432566}
Row: {word=word #01999881, last_ms=1632267567036, id=179432565}
Row: {word=word #01999882, last_ms=1632267567036, id=179432564}
Row: {word=word #01999883, last_ms=1632267567036, id=179432563}
DB Profiler has been interrupted

 */

}
