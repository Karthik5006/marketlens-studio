package app.marketlens
import org.junit.Assert.*
import org.junit.Test
class LegacyImportTest {
 @Test fun keepsStudioConflictsAndCopiesNewNotes(){val current=InvestorState("{\"notes\":{\"TCS\":\"new\"}}");val old=LegacyImport.parse("{\"notes\":{\"TCS\":\"old\",\"RELIANCE\":\"thesis\"}}");LegacyImport.merge(current,old,false);assertEquals("new",current.note("TCS"));assertEquals("thesis",current.note("RELIANCE"));LegacyImport.merge(current,old,true);assertEquals("old",current.note("TCS"))}
 @Test(expected=IllegalArgumentException::class)fun rejectsInvalidPositions(){LegacyImport.parse("{\"positions\":{\"TCS\":{\"symbol\":\"TCS\",\"quantity\":-1,\"average\":100,\"fees\":0}}}")}
 @Test(expected=IllegalArgumentException::class)fun rejectsUnrelatedJson(){LegacyImport.parse("{\"app\":\"other\"}")}
}
