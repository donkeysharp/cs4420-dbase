package junit;

import java.util.ArrayList;

import junit.framework.TestCase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import queries.CrossJoin;
import queries.Operation;
import queries.Query;
import queries.Select;
import queries.TableOperation;

public class SelectTest extends TestCase {

	public static String simpleCrossJoin = "(CROSSJOIN \"A\", \"B\")";
	
	public static String simpleWhere = "(WHERE (EQ (A \"A\") (A \"B\")))";
	
	public static String simpleSelect = "(SELECT" + simpleCrossJoin
		+ simpleWhere + ")";
	
	public static final String crazyQuery = "(project (a \"a\", qa \"R\" \"c\","
		+ "a \"e\")(select(crossJoin \"R\", \"S\" )(where(and(eq  "
		+ "(qa \"R\" \"c\") (qa \"S\" \"c\"))"
		+ "(or(eq (a \"a\") (k string \"leo\") )(lt (a \"e\") (k int 4) ))))))";
	
	public Select select;
	
	@Before
	public void setUp() {
	}

	@After
	public void tearDown() {
	}

	@Test
	public void testCalculateCost() {
	}

	@Test
	public void testSelectWithOneJoin() {
		
		System.out.println();
		System.out.println("testSelectWithOneJoin");
		System.out.println();
		
		select = (Select) Operation.makeOperation(simpleSelect);
		
		assertTrue("Should have 3 children, but have " 
			+ select.getChildCount(), select.getChildCount() == 3);
		
		CrossJoin join = (CrossJoin) select.getTableOne();
		
		//See that tableOne is A from the join
		assertTrue("Table 1 should be A, but was "
				+ ((TableOperation)join.getTableOne()).getTableName(),
				((TableOperation)join.getTableOne()).getTableName().
				equalsIgnoreCase("A"));

		//See that tableTwo is B from the join
		assertTrue("Table 1 should be B, but was "
				+ ((TableOperation)join.getTableTwo()).getTableName(),
				((TableOperation)join.getTableTwo()).getTableName().
				equalsIgnoreCase("B"));
	}

	@Test
	public void testGetChildCount() {
	}

	@Test
	public void testGetParentAttributes() {
		
		Query query = new Query(crazyQuery);
		
		//Check the root
		Operation treeRoot = query.getTreeRoot();
		ArrayList < String > results = treeRoot.getParentAttributes();
		for (int index = 0; index < results.size(); index++) {
			System.out.print(results.get(index) + "\t");
		}
		
		//Check the select
		results = treeRoot.getTableOne().getParentAttributes();
		for (int index = 0; index < results.size(); index++) {
			System.out.print(results.get(index) + "\t");
		}
	}
}
