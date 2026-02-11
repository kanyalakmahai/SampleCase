package com.sample.search.spring.service.impl;

import java.io.File;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.th.ThaiAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.MergePolicy;
import org.apache.lucene.index.IndexWriter.IndexReaderWarmer;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.sample.search.spring.service.AccountLevelService;
import com.sample.search.spring.service.IndexService;
import com.krungsri.aycap.pm.util.DateUtils;

@Service("documentCreatorService")
public class DocumentCreatorServiceImpl implements IndexService,Job {
	private static final String NODE_FIRSTNAME_ENG = "FIRSTNAME_ENG";
	private static final String NODE_FIRSTNAME_THAI = "FIRSTNAME_THAI";
	private static final String NODE_LASTNAME_ENG = "LASTNAME_ENG";
	private static final String NODE_LASTNAME_THAI = "LASTNAME_THAI";
	@Autowired
	private AccountLevelService accountLevelService;
	private boolean create;

	protected IndexWriter indexWriter;
	protected Directory directory;
	protected Analyzer analyzer;
	protected IndexWriterConfig indexWriterConfig;

	public void setCreate(boolean create) {
		this.create = create;
	}

	public void setup() throws Exception {
	}

	@Override
	public void indexDocument() {
		String indexPath = "D:/sample/index";
		File fileDirectory = new File(indexPath);
		if (fileDirectory.list().length > 0) {
			create = false;
		}
		try {
			directory = FSDirectory.open(fileDirectory);
			analyzer = new ThaiAnalyzer();
			indexWriterConfig = new IndexWriterConfig(Version.LATEST, analyzer);
			if (create) {
				indexWriterConfig.setOpenMode(OpenMode.CREATE);
			} else {
				indexWriterConfig.setOpenMode(OpenMode.CREATE_OR_APPEND);
			}
			
			indexWriterConfig.setRAMBufferSizeMB(256.0);	//flush by memory usage instead of document count.
			indexWriterConfig.setUseCompoundFile(false);	//If "Yes", it will require many more file descriptors to be opened by your readers,
															//so set "False" to decrease mergeFactor to avoid hitting file descriptor limits, for tuning performance.
			indexWriter = new IndexWriter(directory, indexWriterConfig);
			
			if (IndexWriter.isLocked(directory)) {
				IndexWriter.unlock(directory);
			}
			int index = 0;
			ResultSet resultSet = accountLevelService.getAccountLevel();
			while (resultSet.next()) {
				Document document = new Document();
				String accountNumber 	= 	resultSet.getString("account_number");
				String firstnameEng 	= 	resultSet.getString("firstname_eng");
				String firstnameThai 	= 	resultSet.getString("firstname_thai");
				String lastnameEng 		= 	resultSet.getString("lastname_eng");
				String lastnameThai 	= 	resultSet.getString("lastname_thai");
				String org 				= 	resultSet.getString("org");
				String logo 			= 	resultSet.getString("logo");
				
				//StringField -> unanalyzed index field
				//TextFiled  -> analyzed index field (e.g. URL that need to extract to be 'token' for using as index.)
				//StoreField -> not index and search field, only store
				
				Field accountNumberField = new StringField("accountNumber", accountNumber, Field.Store.YES); 
				document.add(accountNumberField);
				
				if (firstnameEng != null) {
					Field firstnameEngField = new StringField("firstNameEng", firstnameEng, Field.Store.YES);
					document.add(firstnameEngField);
				}
				if (firstnameThai != null) {
					Field firstnameThaiField = new StringField("firstNameThai", firstnameThai, Field.Store.YES);
					document.add(firstnameThaiField);
				}
				if (lastnameEng != null) {
					Field lastnameEngField = new StringField("lasNameEng", lastnameEng, Field.Store.YES);
					document.add(lastnameEngField);
				}
				if (lastnameThai != null) {
					Field lastnameThaiField = new StringField("lastNameThai", lastnameThai, Field.Store.YES);
					document.add(lastnameThaiField);
				}
				if (org != null) {
					Field orgField = new StoredField("org", org);
					document.add(orgField);
				}
				if (logo != null) {
					Field logoField = new StoredField("logo", logo);
					document.add(logoField);
				}
				indexWriter.addDocument(document);
				System.out.println(index++);
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			try {
				indexWriter.commit();
				indexWriter.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void mergeIndex() throws IOException {
		
	}
	
	@Override
	public void closeIndex() {
		try {
			indexWriter.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		String searchText = "สม*";
		try {
			IndexReader reader = DirectoryReader.open(FSDirectory.open(new File("D:/sample/index")));
			IndexSearcher searcher = new IndexSearcher(reader);
			Analyzer analyzer = new ThaiAnalyzer();
			QueryParser parser = new QueryParser("firstnameThai", analyzer);
			Query query = parser.parse(searchText);
			query.rewrite(reader).toString(); // show the actual query Lucene runs.
			TopDocs results = searcher.search(query, 50);
			ScoreDoc[] hits = results.scoreDocs;
			for (int i = 0; i < hits.length; i++) {
				Document doc = searcher.doc(hits[i].doc);
				System.out.println(doc.get("accountNumber")+" "+ doc.get("firstNameThai"));
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ParseException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void execute(JobExecutionContext context)
			throws JobExecutionException {
				//update
				ResultSet resultSet = accountLevelService.getUpdatedRecord();
				try {
					while (resultSet.next()) {
						//records new release
						Document documentUpdate = new Document();
						String tableColumnNew	= 	resultSet.getString("table_column");
						String oldValueNew 		= 	resultSet.getString("old_value");
						String newValueNew 		= 	resultSet.getString("new_value");
						String accountNumberNew	= 	resultSet.getString("account_number");
						
						//find existing index
						IndexReader reader = DirectoryReader.open(FSDirectory.open(new File("D:/sample/index"))); // default as read-only. Because if read-only, they can avoid synchronizing and work like concurrency (tuning performance)
						IndexSearcher searcher = new IndexSearcher(reader);
						Query accountQ = new TermQuery(new Term("accountNumber", accountNumberNew));
						Query tableColumnQ = new TermQuery(new Term("tableColumn", tableColumnNew));
						Query oldValueQ = new TermQuery(new Term("oldValue", oldValueNew));
						
						BooleanQuery booleanQuery = new BooleanQuery();
						booleanQuery.add(accountQ,  BooleanClause.Occur.MUST);
						booleanQuery.add(tableColumnQ, BooleanClause.Occur.MUST);
						booleanQuery.add(oldValueQ, BooleanClause.Occur.MUST);
						
						//find process
						TopDocs topdocs = searcher.search(booleanQuery, 5);	//this is must be having only one record.
						ScoreDoc[] hits = topdocs.scoreDocs;
						for (int i = 0; i < hits.length; i++) {
							//old records
							Document doc = searcher.doc(hits[i].doc);
							String accountNumberSave = (doc.get("accountNumber") != null?doc.get("accountNumber").trim():"");
							String firstNameEngSave = (doc.get("firstNameEng") != null?doc.get("firstNameEng").trim():"");
							String firstNameThaiSave = (doc.get("firstNameThai") != null?doc.get("firstNameThai").trim():"");
							String lastNameEngSave = (doc.get("lastnameEng") != null?doc.get("lastnameEng").trim():"");
							String lastNameThaiSave = (doc.get("lastnameThai") != null?doc.get("lastnameThai").trim():"");
							
							Field accountNumberField = new StringField("accountNumber", accountNumberSave, Field.Store.YES);	//stored fields must go back to disk for every document.
							documentUpdate.add(accountNumberField);
							
							Field firstnameEngField = new StringField("firstNameEng", ((tableColumnNew != null && tableColumnNew.trim().equals(NODE_FIRSTNAME_ENG))? newValueNew: firstNameEngSave), Field.Store.YES);
							documentUpdate.add(firstnameEngField);
							
							Field firstnameThaiField =  new StringField("firstNameThai", ((tableColumnNew != null && tableColumnNew.trim().equals(NODE_FIRSTNAME_THAI))? newValueNew: firstNameThaiSave), Field.Store.YES);
							documentUpdate.add(firstnameThaiField);
							
							Field lastnameEngField = new StringField("lastNameEng", ((tableColumnNew != null && tableColumnNew.trim().equals(NODE_LASTNAME_ENG))? newValueNew: lastNameEngSave), Field.Store.YES);
							documentUpdate.add(lastnameEngField);
							
							Field lastnameThaiField = new StringField("lastnameThai",  ((tableColumnNew != null && tableColumnNew.trim().equals(NODE_LASTNAME_THAI))? newValueNew: lastNameThaiSave), Field.Store.YES);
							documentUpdate.add(lastnameThaiField);
							
							indexWriter.updateDocument(new Term(doc.get(accountNumberSave)), documentUpdate);
							indexWriter.close();
						}
					}
				} catch (SQLException e) {
					e.printStackTrace();
				} catch (IOException e){
					e.printStackTrace();
				} catch (Exception e){
					e.printStackTrace();
				} finally {
					try {
						indexWriter.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
	}
}

	
	
