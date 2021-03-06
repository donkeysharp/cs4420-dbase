package queries;

import java.util.ArrayList;

import dbase.Attribute;
import dbase.Database;
import dbase.Iterator;
import dbase.Relation;
import dbase.RelationHolder;
import dbase.SystemCatalog;

public class Project extends Operation {

	protected ArrayList < String > attributes;
	
	/**This will create a new instance of Project.  It will find what to 
	 * project and from where from the statement passed.
	 * @param statement The literal of the project statement.
	 */
	public Project (final String statement) {
		
		setType(QueryParser.PROJECT);
		
		ArrayList < String > parts = QueryParser.parseStatementParts(statement);
		//Find the list of attributes to project
		attributes = QueryParser.parseQueryAttributes(
			parts.get(QueryParser.PROJECT_ATTRIBUTES_INDEX));
				
		//Find the table its coming from
		tableOne = Operation.makeOperation(
			parts.get(QueryParser.PROJECT_FROM_INDEX));
		tableOne.setParent(this);
	}
	
	/**This will make an empty project, with onlt the type initialized.
	 */
	public Project () {
		setType(QueryParser.PROJECT);
	}
	
	/**This method will return the cost of performing this Project which is
	 * basically the size of the table it is projecting from in blocks.
	 * @return The cost of this Project.
	 */
	public long calculateCost() {
		return tableOne.calculateCost();
	}
	
	public boolean execute(){
		if (!tableOne.execute()){
			System.out.println("in here?");
			return false;
		}
		Relation r = RelationHolder.getRelationHolder().getRelation(this.resultTableID);
		Relation s = RelationHolder.getRelationHolder().getRelation(tableOne.getResultTableID());
		String [] attnames = new String[attributes.size()];
		if (r == null) return false;
		
		
		for (int j=0; j<attnames.length; j++){
			Attribute att = s.getAttributeByName(attributes.get(j).trim());
			//r.addAttribute(att.getName(), att.getType(), Database.getCatalog().getSmallestUnusedAttributeID());
			attnames[j] = att.getName();
		}
		System.out.println(attnames);
		Iterator it = new Iterator (s);
		String [] newattvals = new String[attnames.length];
		while (it.hasNext()){
			System.out.println("onerow");
			String [] oldattvals = it.getNext();
			for (int j=0; j<attributes.size(); j++){
				for (Attribute a: s.getAttributes()){
					System.out.println(a.getName());
				}
				int idx = s.getIndexByName(attributes.get(j));
				newattvals[j] = oldattvals[idx];
			}
			if (!Database.getCatalog().insert(this.resultTableID, attnames, newattvals) )return false;
		}
		return true;
		
		
	}
	
	/**This method caueses the project to generate the table that is the
	 * result of the PROJECT.
	 */
	public void generateTemporaryTable() {
		
		//Get the schema for the thing below it
		Relation source = RelationHolder.getRelationHolder().
			getRelation(tableOne.getResultTableID());
		
		//Create the new table
		Relation result = new Relation(QueryParser.RESULT + resultTableID,
			resultTableID);
		RelationHolder.getRelationHolder().addRelation(result);
		
		//System.out.println("PROJECT TABLE SOURCE: " + source);
		//System.out.println("PROJECT ATTRIBUTES: " + attributes);
		//System.out.println("SOURCE ATTRIBUTES: " + source.getAttributes());
		
		//For each attribute, get the real thing from the source
		for (int index = 0; index < attributes.size(); index++) {
			
			String currentName = attributes.get(index);
			//System.out.println("PROJECT CURRENT ATTRIBUTE: " + currentName);
			//Use tempCurrentName cause it may be qualified
			Attribute currentAttribute 
				= source.getAttributeByName(currentName);
			
			//Give it the full name when we make it though
			Attribute newAttribute = new Attribute(
				currentName, currentAttribute.getType(), 0);
			
			result.addAttribute(newAttribute);
		}
	}
	
	/**This method will return the attributes of this Projection and those
	 * required by its parents.
	 */
	public ArrayList < String > getParentAttributes() {
		
		//Get the attributes of all of the parents if there is a parent
		ArrayList < String > parentAttributes;
		if (parent != null) {
			parentAttributes = (ArrayList) parent.getParentAttributes().clone();
		} else { //If no parents then return those of this projection only
			return (ArrayList) this.attributes.clone();
		}
		
		//Merge the parent and this one if it exists
		for (int index = 0; index < attributes.size(); index++) {
			boolean add = true;
			String attribute = attributes.get(index);
			for (int inner = 0; inner < parentAttributes.size(); inner++) {
				//If the are they same, then don't add
				String parentAttribute = parentAttributes.get(inner);
				if (attribute.equalsIgnoreCase(parentAttribute)) {
					add = false;
					break;
				}
			}
			if (add) {
				parentAttributes.add(attribute);
			}
		}
		
		return parentAttributes;
	}
	
	/**This method will return whether or not the Project allows children as
	 * per the <code>TreeNode</code> interface.
	 * @return <code><b>true</b></code> becase a Project must always 
	 * have children.
	 */
	public boolean getAllowsChildren() {
		return true;
	}
	
	/**This method returns the value of attributes.
	 * @return the attributes
	 */
	public ArrayList < String > getAttributes() {
		return attributes;
	}

	/**This method will return the number of children that this Project has
	 * as per the TreeNode interface.
	 * @return The number of children of this node.
	 */
	public int getChildCount() {
		int childCount = 1 + tableOne.getChildCount();
		return childCount;
	}
	
	public ArrayList < String > getRelations() {
		
		ArrayList < String > relations = new ArrayList < String > ();
		
		//If this is a one level dealie, that is the tableOne is just a regular
		//old table then just return that name
		if (tableOne.getType().equalsIgnoreCase(QueryParser.TABLEOPERATION)) {
			relations.add(((TableOperation) tableOne).getTableName());
		} else { //Otherwise, ask the stuff below it for its tables
			relations = tableOne.getRelations(); 
		}
		
		return relations;
	}

	public ArrayList < String > getTreeAttributes() {
		
		//Merge the list of attributes from this one and those below it
		ArrayList < String > subAttributes = tableOne.getTreeAttributes();
		
		//Merge and return the list
		for (int index = 0; index < attributes.size(); index++) {
			
			boolean add = true;
			for (int inner = 0; inner < subAttributes.size(); inner++) {
				if (attributes.get(index).
					equalsIgnoreCase(subAttributes.get(inner))) {
					add = false;
				}
			}
			if (add) {
				subAttributes.add(attributes.get(index));
			}
		}
		return subAttributes;
	}
	
	/**The project doesn't really have any conditions associated with it, but
	 * suff below it might.  You know, just in case it does.
	 * @return All of the SimpleConditions in the tree below this project.
	 */
	public ArrayList < SimpleCondition > getTreeConditions() {
	
		return tableOne.getTreeConditions();
		
	}
	
	/**Says whether or not the Projection is a Leaf.  
	 * Always false, a Project is never a Leaf in a query tree.
	 * @return <code><b>false</b></code> because Project statements
	 * aren't leaves.
	 */
	public boolean isLeaf() {
		return false;
	}
	
	/**This method will set the value of attributes.
	 * @param newAttributes The new value of attributes.
	 */
	public void setAttributes(final ArrayList < String > newAttributes) {
		this.attributes = newAttributes;
	}
	
	public String toString() {
		
		String string = "|"; 
		
		string += this.queryID + "\t|";
		string += this.executionOrder + "\t|";
		string += this.type + "\t|";
		for (int index = 0; index < attributes.size(); index++) {
			string += attributes.get(index) + ", ";
		}
		string += "|";
		string += tableOne.getResultTableID() + "\t|";
		string += resultTableID + "\t|";
		string += "\n";
		
		return string;
	}
}
