/*
 * BufferManager.java
 *
 * Created on March 6, 2007, 5:20 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package dbase;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * @author andrewco
 */
public class BufferManager {
	
	/**For singleton use*/
	private static BufferManager thisbuffer;
    
    /**The Buffer Implenetation.*/
    private ByteBuffer [] buffer = new ByteBuffer[4000];
    
    /**The offset of the block in the whole relation and block going to 
     * the physical address.  At 1000, this limits the DB to 1000 relations.
     * because the physical sddres takes the form of block*offset+relation.
     */
    public static final int BLOCK_ADDRESS_OFFSET = 10000;
    
    /**The index indicating the time of a block for the clock algorithm in 
     * the lookUp table.
     */ 
    public static final int TIME_INDEX = 1;
    
    /**The index of the physical address in the lookup table.*/
    public static final int PHYSICAL_INDEX = 0;
    
    /**This is a null character for creating empty spaces in blocks.*/
    public static final char NULL_CHARACTER = '\0';
    
    /**A lookup table for the buffer.*/
    private long [][] lookUpTable = new long [4000][2];
    
    /**The current position of the clock.*/
    private long time = 0;
    
    /**This is the StorageManager which buffermanager will ask for reads
     * and writes to disk.
     */
    private StorageManager storage;
    
    /**Creates a new instance of BufferManager.*/
    private BufferManager() {
    	storage = new StorageManager();
    	for (int i = 0; i < 4000; i++) {
    		lookUpTable[i][PHYSICAL_INDEX] = -1;
    		lookUpTable[i][TIME_INDEX] = 0;
    	}
    }
    
    
    /**
     * The Singleton accessor for BufferManagers.  This is the only way to get a BufferManager.
     * @return The copy of BufferManager for the system.
     */
    public static BufferManager getBufferManager() {
    	if (thisbuffer != null) {
    		return thisbuffer;
    	} else {
    		thisbuffer = new BufferManager();
    		return thisbuffer;
    	}
    }
    
    /**Writes all non-pinned blocks to disk to clear the buffer.
     *@return Whether ot not the fluxh succeeded.
     */
    public boolean flush() {
    	//TODO write flush.
        return true;
    }
    
    
    /**Finds a spot to free up for a new block in memory and return it's
     *logical address.
     *@return The logical address of the freed block.
     */
    private long freeSpace() {
    	//go till we find one
	    while (true) {
	    	//if it is 0 return it
			if (lookUpTable[(int) time][TIME_INDEX] == 0) {
				return time;
			} else {
				//if it's pinned just move on
				if (lookUpTable[(int) time][TIME_INDEX] == -1) {
					time++;
				} else {
					//if it's not pinned decrement and move on
					lookUpTable[(int) time][TIME_INDEX]--;
					if (time == 3999) {
						System.out.println("Back to 0");
						time = 0;
					} else {
						time++;
					}
				}
			}
		}
    }
    
    /**This method will add the given block with the given physical address
     * to the memory buffer.  It will call the freeSpace method to find or 
     * create a free space for the new block.
     * @param block The block to be placed in the memory buffer.
     * @param physical The physical address of the block.
     * @return Whether or not the block was successfully added to the buffer.
     */
    private long addToBuffer(final ByteBuffer block,
    		final long physical) {
    	//First get a space in the buffer to add the new block to.
    	long logicalAddress = freeSpace();
    	
    	//Now add it to the buffer and the lookuptable
    	buffer[(int) logicalAddress] = block;
    	lookUpTable[(int) logicalAddress][PHYSICAL_INDEX] = physical;
    	//Then set its clock value to 1
    	lookUpTable[(int) logicalAddress][TIME_INDEX] = 1;
    	
    	return logicalAddress;
    }
    
    /**Pins a block currently in memory.
     *@param relation The relation containing the block to be pinned.
     *@param block  The block in the relation to be pinned.
     *@return Whether or not the block was successfully pinned.
     */    
    public boolean pin(final int relation, final long block) {
    	//make physical address
    	long address = makePhysicalAddress(relation, block);
    	//look for that address
    	for (int logical = 0; logical < lookUpTable.length; logical++) {
    		if (address == lookUpTable[logical][PHYSICAL_INDEX]) {
    			//see if it is pinned
    			if (lookUpTable[logical][TIME_INDEX] != (long) -1) {
    				//found and unpinned
    				lookUpTable[logical][TIME_INDEX] = -1;
    				return true;
    			}
    			//found but not pinned
    			return false;
    		}
    	}
    	//not found
    	return false;
    }
    
    /**Pins a block currently in memory.
     *@param logical the logical address to be pinned.
     *@return Whether or not the block was successfully pinned.
     */    
    public boolean pin(final long logical) {
    	//make physical address
    	long address = logical;
    	//look for that address
    	 			//see if it is pinned
    			if (lookUpTable[(int)address][TIME_INDEX] != (long) -1) {
    				//found and unpinned
    				lookUpTable[(int)address][TIME_INDEX] = -1;
    				return true;
    			}
    			//found but not pinned
    			return false;
    }
    
    /**Reads a block from memory, will load block into memory if not already in
     *the buffer.
     *@param relation The relation that the requested block is in.
     *@param block The block from the specified relation.
     *@return The block requested in a MappedByteBuffer.
     */
    public ByteBuffer read(final int relation, final long block) {
    	
    	//First generate the physical address of the block
    	long physicalAddress = makePhysicalAddress(relation, block);
    	
    	//Then see if it is already in memory.  If it is then get it from
    	//the buffer and return it.  This is the point of a buffer.
    	if (isInBuffer(physicalAddress)) {
    		return this.getFromBuffer(physicalAddress);
    	}
    	
    	//If it isn't already in the buffer, then we need to ask storage manager
    	//to get it for us.
    	ByteBuffer result = storage.read(relation, block);
    	//Then we need to put it in the buffer because it was just read.
    	addToBuffer(result, physicalAddress);
    	
        return result;
    }
    
    /**
     * Used to read in only for catalog use.
     * @param catalog The FileChannel of the Catalog File.
     * @param block the block number.
     * @return The ByteBuffer denoted.
     */
    public ByteBuffer readRelationCatalog(final String catalog, 
    		final long block) {
    	
    	//First generate the physical address of the block
    	long physical = makeRelationAddress(block);
    	
    	//Then see if it is already in memory.  If it is then get it from
    	//the buffer and return it.  This is the point of a buffer.
    	if (isInBuffer(block)) {
    		return this.getFromBuffer(block);
    	}
    	
    	//If it isn't already in the buffer, then we need to ask storage manager
    	//to get it for us.
    	ByteBuffer result = storage.read(catalog, block);
    	//Then we need to put it in the buffer because it was just read.
    	pin(addToBuffer(result, block));
    	
    	    	
        return result;
    }
    
    /**
     * Used to read in only for catalog use.
     * @param catalog The FileChannel of the Catalog File.
     * @param block the block number.
     * @return The ByteBuffer denoted.
     */
    public ByteBuffer readAttributeCatalog(String catalog, final long block) {
    	
    	//Make the phony physical address for attribute blocks
    	long physicalAddress = makeAttributeAddress(block);
    	
    	//Then see if it is already in memory.  If it is then get it from
    	//the buffer and return it.  This is the point of a buffer.
    	if (isInBuffer(physicalAddress)) {
    		return this.getFromBuffer(physicalAddress);
    	}
    	
    	//If it isn't already in the buffer, then we need to ask storage manager
    	//to get it for us.
    	ByteBuffer result = storage.read(catalog, block);
    	//Then we need to put it in the buffer because it was just read.
    	pin(addToBuffer(result, physicalAddress));
    	    	
        return result;
    }
    
    /**Removes the pin from a block in memory.
     *@param relation The ID of the relation containing the block to be 
     *unpinned.
     *@param block The block in the relation to be unpinned.
     *@return Whether or not the block was successfully unpinned.
     */
    public boolean unpin(final int relation, final long block) {
    	//make physical address
    	long address = makePhysicalAddress(relation, block);
    	//look for that address
    	for (int logical = 0; logical < lookUpTable.length; logical++) {
    		if (address == lookUpTable[logical][0]) {
    			//see if it is pinned
    			if (lookUpTable[logical][TIME_INDEX] == (long) -1) {
    				//found and pinned
    				lookUpTable[logical][TIME_INDEX] = 1;
    				return true;
    			}
    			//found but not pinned
    			return false;
    		}
    	}
    	//not found
    	return false;
    }
    
    /**Writes the specified block to disk, given the physical address.
     * @param address The <b>physical</b> address to be written to disk.
     * @param data the ByteBuffer of data to be written to the disk.
     * @return whether the block was successfully written.
     */
    public boolean writePhysical(final long address, final ByteBuffer data) {
    	//Just write the specified data to the address specified.
    	storage.write((int) address / BLOCK_ADDRESS_OFFSET,
    		address % BLOCK_ADDRESS_OFFSET, data);
    	return true;
    }
    
    /**Writes the specified block to disk. given the logical address.
     * @param logical The <b>logical</b> address to be written to disk.
     * @return whether the block was successfully written.
     */
    public boolean writeLogical(final long logical) {
     	//Find the block to be written given the logical address
    	ByteBuffer block = buffer[(int) logical];
    	//Find the physical address of the block
    	long physical = lookUpTable[(int) logical][PHYSICAL_INDEX];
    	//Write the block to disk
    	storage.write((int) physical % BLOCK_ADDRESS_OFFSET,
    		physical / BLOCK_ADDRESS_OFFSET, block);	   
    	return true;
    }
    
    /**
     * Used to write to relation Catalog.
     * @param catalog The Catalog FileChannel.
     * @param address The Address to write to.
     * @param block The Block to Write.
     * @return whether it wrote.
     */
    public boolean writeRelCatalog(String catalog, long address, ByteBuffer block) {
    	storage.write(catalog, (int)(address), block);
    	return true;
    }
    
    /**
     * Used to write to Attribute Catalog.
     * @param catalog The Catalog FileChannel.
     * @param address The Address to write to.
     * @param block The Block to Write.
     * @return whether it wrote.
     */
    public boolean writeAttCatalog(String catalog, long address, ByteBuffer block) {
    	storage.write(catalog, (int)(address), block);
    	return true;
    }
    
    
    /**This method will return whether or not the specified block is pinned in
     * memory. Please note all Physical Addresses are in the format: &&####%%
     * where #### is the block number, %% is the relation ID, and && is the 
     * record number on the block
     * @param relation The relation containing the block.
     * @param block The block inside of that relation.
     * @return Whether or not the block is pinned.
     */
    public boolean isPinned(final int relation, final long block) {
    	//make physical address
    	long address = makePhysicalAddress(relation, block);
    	//look for that address
    	for (int logical = 0; logical < lookUpTable.length; logical++) {
    		if (address == lookUpTable[logical][PHYSICAL_INDEX]) {
    			//see if it is pinned
    			if (lookUpTable[logical][TIME_INDEX] == (long) -1) {
    				//found and pinned
    				return true;
    			}
    			//found but not pinned
    			return false;
    		}
    	}
    	//not found
    	return false;
    }
    
    /**This method is used to determine if a given address is already in
     * the memory buffer. This exists because I don't know if we want this 
     * one, or it's overloaded companion.
     * @param relation The ID of the relation containing the block we are 
     * checking.
     * @param block The block in the relation.
     * @return Whether or not the specified block is already in the buffer.
     */
    public boolean isInBuffer(final int relation, final long block) {
    	//Return the value provided by the overloaded function
    	return (isInBuffer(makePhysicalAddress(relation, block)));
    }
    
    /**This method will return whether or not a specified physical address is
     * currently in the memory buffer.
     * @param physical The physical address whos status we are checking.
     * @return Whether or not the block with the specified address is currently
     * in the buffer.
     */
    public boolean isInBuffer(final long physical) {
    	//Loop through the entire set of logical addresses to see if any of 
    	//them map to the specified physical address
    	for (int logical = 0; logical < lookUpTable.length; logical++) {
    		//If one of the logical addresses maps to the physical, return true
    		if (physical == lookUpTable[logical][PHYSICAL_INDEX]) {
    			return true;
    		}
    	}
    	//If the address is not in memory then return false
    	return false;
    }
    
    /**This method will translate a relation ID and block number in that
     * relation into a physical address.
     * @param relation The ID of the relation we are making the address for.
     * @param block The block in that relation.
     * @return The physical address produced by the combination of the relation
     * and the block.
     */
    public static long makePhysicalAddress(final int relation,
    		final long block) {
    	return block + BLOCK_ADDRESS_OFFSET * (relation);	
    	
    }
    
    /**This method will translate an attribute catalog block number
     * into a physical address.
     * @param block The block in the attribute catalog.
     * @return The physical address produced by the combination of the relation
     * and the block.
     */
    public static long makeAttributeAddress(final long block) {
    	return SystemCatalog.ATT_OFFSET + block;
    }
    
    /**This method will translate a relation catalog block number
     * into a physical address.
     * @param block The block in the relation catalog.
     * @return The physical address produced by the combination of the relation
     * and the block.
     */
    public static long makeRelationAddress(final long block) {
    	return SystemCatalog.REL_OFFSET + block;
    }
    
    
    /**This method takes in a physical address, looks up its logical address
     * in the buffer and returns the MappedByteBuffer representing the 
     * physical address.  If this is called and the physical address specified
     * is not in the buffer, the program will exit.  Make sure the block
     * with the address is already in the buffer.
     * @param physical  The physical address of the block we want.
     * @return The MappedByteBuffer of that block.
     */
    private ByteBuffer getFromBuffer(final long physical) {
    	//Loop through the lookupTable to see if the physical address is in
    	//Here and return the MappedByteBuffer containing it if so.
    	for (int logical = 0; logical < lookUpTable.length; logical++) {
    		//If one of the logical addresses maps to the physical, return true
    		if (physical == lookUpTable[logical][PHYSICAL_INDEX]) {
    			lookUpTable[logical][TIME_INDEX]++;
    			return buffer[logical];
    		}
    	}
    	
    	//If we've gotten here and we haven't returned a block, some shit has
    	//happened.  Time to bail.
    	System.out.println("Requested block was not in the buffer.");
    	System.out.println("BufferManager.getFromBuffer.  Only ask for blocks"
    		+ " already in the buffer.  Dick.");
    	System.exit(1);
    	return null;
    }

    /**This method will create a ByteBuffer of BLOCK_SIZE with '\0' for its
     * entire contents.
     * @return  An empty block full of '\0'.
     */
    public static ByteBuffer getEmptyBlock() {
    	//Generate and return a block with all null characters
    	byte [] block = new byte [StorageManager.BLOCK_SIZE];
    	//Fill it with null characters as bytes
    	for (int currentByte = 0; currentByte < StorageManager.BLOCK_SIZE; 
    		currentByte++) {
    		block[currentByte] = (byte) '\0';	      
    	}
    	
    	//Return the block in a ByteBuffer
    	return ByteBuffer.wrap(block);
    }
}
