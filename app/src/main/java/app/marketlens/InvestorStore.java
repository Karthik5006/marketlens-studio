package app.marketlens;
import android.content.*;

final class InvestorStore {
 private final SharedPreferences preferences;private InvestorState state;String issue="";
 InvestorStore(Context context){preferences=context.getSharedPreferences("investor-v11",0);try{state=new InvestorState(preferences.getString("state","{}"));}catch(Exception e){issue="Saved investor data could not be read. The original is retained; export it before making changes.";try{state=new InvestorState("{}");}catch(Exception impossible){throw new IllegalStateException(impossible);}}}
 synchronized InvestorState snapshot(){try{return new InvestorState(state.toString());}catch(Exception e){throw new IllegalStateException(e);}}
 interface Edit {void change(InvestorState state)throws Exception;}
 synchronized void edit(Edit edit)throws Exception{if(!issue.isEmpty())throw new IllegalStateException(issue);InvestorState copy=new InvestorState(state.toString());edit.change(copy);if(!preferences.edit().putString("state",copy.toString()).commit())throw new IllegalStateException("Your change could not be saved. Please try again.");state=copy;}
 synchronized String export(){return preferences.getString("state","{}");}
}
