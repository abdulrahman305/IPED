package iped.app.ui.filterdecisiontree;

import iped.viewers.api.IFilter;

public class FilterNode extends DecisionNode {
    IFilter filter;
    boolean inverted;

    public FilterNode(IFilter filter) {
        this.filter = filter;
    }

    public IFilter getFilter() {
        return filter;
    }
    
    @Override
    public String toString() {
        return filter.toString();
    }

    public boolean isInverted() {
        return inverted;
    }

    public void setInverted(boolean inverted) {
        this.inverted = inverted;
    }

    public void invert() {
        this.inverted = !inverted;        
    }

}
