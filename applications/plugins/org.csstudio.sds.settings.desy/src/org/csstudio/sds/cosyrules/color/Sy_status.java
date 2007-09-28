package org.csstudio.sds.cosyrules.color;

import org.csstudio.sds.model.logic.IRule;
import org.eclipse.swt.graphics.RGB;

/**
 * Color rule Sy-status, translated from an ADL file.
 * 
 * @author jbercic
 *
 */
public final class Sy_status implements IRule {
	/**
	 * The ID for this rule.
	 */
	public static final String TYPE_ID = "cosyrules.color.sy-status";

	/**
	 * Standard constructor.
	 */
	public Sy_status() {
	}

	/**
	 * {@inheritDoc}
	 */
	public Object evaluate(final Object[] arguments) {
		if ((arguments != null) && (arguments.length > 0)) {
		    double d = 0.0;
            if (arguments[0] instanceof Double) {
                 d = (Double) arguments[0];
            }else if (arguments[0] instanceof Long) {
                d = ((Long)  arguments[0]).doubleValue();
            }				
			if (Math.abs(d-0.0)<0.00001) {
				return new RGB(78,165,249);
			}
			if (Math.abs(d-1.0)<0.00001) {
				return new RGB(45,127,0);
			}
			if (Math.abs(d-2.0)<0.00001) {
				return new RGB(225,144,21);
			}
			if (Math.abs(d-3.0)<0.00001) {
				return new RGB(190,25,11);
			}
			if (Math.abs(d-4.0)<0.00001) {
				return new RGB(251,243,74);
			}
			if (Math.abs(d-5.0)<0.00001) {
				return new RGB(253,0,0);
			}
			if (Math.abs(d-6.0)<0.00001) {
				return new RGB(115,255,107);
			}
			if (Math.abs(d-7.0)<0.00001) {
				return new RGB(51,153,0);
			}
			if (Math.abs(d-8.0)<0.00001) {
				return new RGB(26,115,9);
			}
			if (Math.abs(d-9.0)<0.00001) {
				return new RGB(145,145,145);
			}
			if (Math.abs(d-10.0)<0.00001) {
				return new RGB(235,241,181);
			}
		}

		return new RGB(0,0,0);
	}
}
