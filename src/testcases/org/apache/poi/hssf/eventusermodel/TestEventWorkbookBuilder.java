/* ====================================================================
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
==================================================================== */

package org.apache.poi.hssf.eventusermodel;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

import org.apache.poi.hssf.HSSFTestDataSamples;
import org.apache.poi.hssf.eventusermodel.EventWorkbookBuilder.SheetRecordCollectingListener;
import org.apache.poi.hssf.model.FormulaParser;
import org.apache.poi.hssf.model.Workbook;
import org.apache.poi.hssf.record.FormulaRecord;
import org.apache.poi.hssf.record.Record;
import org.apache.poi.hssf.record.formula.Ref3DPtg;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hssf.util.SheetReferences;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
/**
 * Tests for {@link EventWorkbookBuilder}
 */
public final class TestEventWorkbookBuilder extends TestCase {
	private MockHSSFListener mockListen;
	private SheetRecordCollectingListener listener;
	
	public void setUp() {
		HSSFRequest req = new HSSFRequest();
		mockListen = new MockHSSFListener();
		listener = new SheetRecordCollectingListener(mockListen);
		req.addListenerForAllRecords(listener);
		
		HSSFEventFactory factory = new HSSFEventFactory();
		try {
			InputStream is = HSSFTestDataSamples.openSampleFileStream("3dFormulas.xls");
			POIFSFileSystem fs = new POIFSFileSystem(is);
			factory.processWorkbookEvents(req, fs);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	} 
	
	public void testBasics() throws Exception {
		assertNotNull(listener.getSSTRecord());
		assertNotNull(listener.getBoundSheetRecords());
		assertNotNull(listener.getExternSheetRecords());
	}
	
	public void testGetStubWorkbooks() throws Exception {
		assertNotNull(listener.getStubWorkbook());
		assertNotNull(listener.getStubHSSFWorkbook());
		
		assertNotNull(listener.getStubWorkbook().getSheetReferences());
		assertNotNull(listener.getStubHSSFWorkbook().getSheetReferences());
	}
	
	public void testContents() throws Exception {
		assertEquals(2, listener.getSSTRecord().getNumStrings());
		assertEquals(3, listener.getBoundSheetRecords().length);
		assertEquals(1, listener.getExternSheetRecords().length);
		
		assertEquals(3, listener.getStubWorkbook().getNumSheets());
		
		SheetReferences ref = listener.getStubWorkbook().getSheetReferences();
		assertEquals("Sh3", ref.getSheetName(0));
		assertEquals("Sheet1", ref.getSheetName(1));
		assertEquals("S2", ref.getSheetName(2));
	}
	
	public void testFormulas() throws Exception {
		FormulaRecord fr;
		
		// Check our formula records
		assertEquals(6, mockListen._frecs.size());
		
		Workbook stubWB = listener.getStubWorkbook();
		assertNotNull(stubWB);
		HSSFWorkbook stubHSSF = listener.getStubHSSFWorkbook();
		assertNotNull(stubHSSF);
		
		// Check these stubs have the right stuff on them
		assertEquals("Sheet1", stubWB.getSheetName(0));
		assertEquals("S2", stubWB.getSheetName(1));
		assertEquals("Sh3", stubWB.getSheetName(2));
		
		// Check we can get the formula without breaking
		for(int i=0; i<mockListen._frecs.size(); i++) {
			fr = (FormulaRecord)mockListen._frecs.get(i);
			FormulaParser.toFormulaString(stubHSSF, fr.getParsedExpression());
		}
		
		// Peer into just one formula, and check that
		//  all the ptgs give back the right things
		List ptgs = ((FormulaRecord)mockListen._frecs.get(0)).getParsedExpression();
		assertEquals(1, ptgs.size());
		assertTrue(ptgs.get(0) instanceof Ref3DPtg);
		
		Ref3DPtg ptg = (Ref3DPtg)ptgs.get(0);
		assertEquals("Sheet1!A1", ptg.toFormulaString(stubHSSF));
		
		
		// Now check we get the right formula back for
		//  a few sample ones
		
		// Sheet 1 A2 is on same sheet
		fr = (FormulaRecord)mockListen._frecs.get(0);
		assertEquals(1, fr.getRow());
		assertEquals(0, fr.getColumn());
		assertEquals("Sheet1!A1", FormulaParser.toFormulaString(stubHSSF, fr.getParsedExpression()));
		
		// Sheet 1 A5 is to another sheet
		fr = (FormulaRecord)mockListen._frecs.get(3);
		assertEquals(4, fr.getRow());
		assertEquals(0, fr.getColumn());
		assertEquals("'S2'!A1", FormulaParser.toFormulaString(stubHSSF, fr.getParsedExpression()));
		
		// Sheet 1 A7 is to another sheet, range
		fr = (FormulaRecord)mockListen._frecs.get(5);
		assertEquals(6, fr.getRow());
		assertEquals(0, fr.getColumn());
		assertEquals("SUM(Sh3!A1:A4)", FormulaParser.toFormulaString(stubHSSF, fr.getParsedExpression()));
		
		
		// Now, load via Usermodel and re-check
		InputStream is = HSSFTestDataSamples.openSampleFileStream("3dFormulas.xls");
		POIFSFileSystem fs = new POIFSFileSystem(is);
		HSSFWorkbook wb = new HSSFWorkbook(fs);
		assertEquals("Sheet1!A1", wb.getSheetAt(0).getRow(1).getCell(0).getCellFormula());
		assertEquals("SUM(Sh3!A1:A4)", wb.getSheetAt(0).getRow(6).getCell(0).getCellFormula());
	}
	
	private static final class MockHSSFListener implements HSSFListener {
		public MockHSSFListener() {}
		private final List _records = new ArrayList();
		private final List _frecs = new ArrayList();

		public void processRecord(Record record) {
			_records.add(record);
			if(record instanceof FormulaRecord) {
				_frecs.add(record);
			}
		}
	}
}