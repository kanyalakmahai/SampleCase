package com.sample.struts2.action.workflow.dispute;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.struts2.convention.annotation.Action;
import org.apache.struts2.convention.annotation.InterceptorRef;
import org.apache.struts2.convention.annotation.Namespace;
import org.apache.struts2.convention.annotation.ParentPackage;
import org.apache.struts2.convention.annotation.Result;
import org.apache.struts2.convention.annotation.ResultPath;

import com.sample.struts2.action.workflow.WorkflowAction;
import com.sample.struts2.action.workflow.WorkflowForm;
import com.sample.struts2.bean.MessageBean;
import com.sample.struts2.bean.TransactionBean;
import com.sample.struts2.constant.SystemConstant;
import com.sample.struts2.dao.workflow.impl.CommonsDAOImpl;
import com.sample.struts2.dao.workflow.inf.CommonsDAO;
import com.sample.struts2.model.workflow.bean.Workflow;
import com.sample.struts2.service.workflow.impl.CommonsServiceImpl;
import com.sample.struts2.service.workflow.impl.DisputeServiceImpl;
import com.sample.struts2.service.workflow.impl.FeeAdjustmentServiceImpl;
import com.sample.struts2.service.workflow.impl.TransactionHelperServiceImpl;
import com.sample.struts2.service.workflow.inf.CommonsService;
import com.sample.struts2.service.workflow.inf.DisputeService;
import com.sample.struts2.service.workflow.inf.FeeAdjustmentService;
import com.sample.struts2.service.workflow.inf.TransactionHelperService;
import com.sample.struts2.util.LoggerService;
import com.sample.ws.service.vplus.AccountInfomationService;
import com.sample.ws.service.vplus.AccountInfomationServiceImpl;

@ParentPackage("workflowPackage")
@Namespace("/workflow/dispute")
@ResultPath("/")
public class DisputeAction extends WorkflowAction {

        private String curtabcut;
        private String totalTransaction;
        private String userFullname;
       
        public String getCurtabcut() {
                return curtabcut;
        }

        public void setCurtabcut(String curtabcut) {
                this.curtabcut = curtabcut;
        }

        public String getTotalTransaction() {
                return totalTransaction;
        }

        public void setTotalTransaction(String totalTransaction) {
                this.totalTransaction = totalTransaction;
        }
       

        private DisputeForm form = new DisputeForm();

        private DisputeService disputeService = new DisputeServiceImpl();

        private CommonsService commonsService = new CommonsServiceImpl();

        public void init(WorkflowForm form) {
                super.init(form);
        }

        @Action(value = "index", results = { @Result(name = SUCCESS, location = "/pages/workflow/dispute.jsp") }, interceptorRefs = { @InterceptorRef("workflowInterceptorStack") })
        public String index() throws Exception {
                MessageBean MESSAGE = new MessageBean();
               
                LoggerService.log.info("Dispute Index Start");
               
                try {
                        this.init(form);

                        BigDecimal workflowId = form.getWorkflowId();
               
                        TransactionHelperService transactionHelper = new TransactionHelperServiceImpl();
                        String accountNumber = this.getAccountNo();

                        session.removeAttribute("trxSum");

                        session.setAttribute("DEFAULT_FEE", commonsService.getTextByCode(this.getWorkflowTypeId(), "AMOUNT", "FEE"));
                        session.removeAttribute(SystemConstant.SESSION.TOTAL_TRANSACTION);
                        session.removeAttribute(SystemConstant.SESSION.TOTAL_AMOUNT);

                        List<HashMap> suspenseList = commonsService.getCodeByWorkflowType(this.getWorkflowTypeId(), SystemConstant.GROUP_CODE.DISPUTE_SUSPENSE);
                        session.setAttribute(SystemConstant.SESSION.SUSPENSE_TYPE_LIST, suspenseList);

                        List<HashMap> disputeReasonList = commonsService.getCodeByWorkflowType(this.getWorkflowTypeId(), SystemConstant.GROUP_CODE.DISPUTE_REASON);
                        session.setAttribute(SystemConstant.SESSION.DISPUTE_REASON, disputeReasonList);

                        List<HashMap> disputeDocList = commonsService.getCodeByWorkflowType(this.getWorkflowTypeId(), SystemConstant.GROUP_CODE.DISPUTE_DOC);
                        session.setAttribute(SystemConstant.SESSION.DISPUTE_DOC, disputeDocList);

                        if (workflowId != null && workflowId.intValue() > 0) {
                                form.setWorkflowId(workflowId);
                                ArrayList<DisputeBean> dataList = disputeService.getTransaction(workflowId);
                                this.session.setAttribute("trxSum", dataList);

                        } else {

                                String region = (String) this.getAccountInfo().get(SystemConstant.COLUMN.REGION);

                                String suspenseText1 = "";
                                String suspenseText2 = "";
                                for (int ix = 0; ix < disputeReasonList.size(); ix++) {
                                        HashMap map = (HashMap) disputeReasonList.get(ix);
                                        suspenseText1 = suspenseText1 + ((String) map.get("ID")).trim() + "#";
                                        suspenseText2 = suspenseText2 + ((String) map.get("TEXT")).trim() + "#";
                                }

                                suspenseText1 = suspenseText1.substring(0, suspenseText1.length() - 1);
                                suspenseText2 = suspenseText2.substring(0, suspenseText2.length() - 1);

                                session.setAttribute(SystemConstant.SESSION.SUSPENSE_TEXT1, suspenseText1);
                                session.setAttribute(SystemConstant.SESSION.SUSPENSE_TEXT2, suspenseText2);

                                ArrayList<String> cycleList = transactionHelper.getCycleCuts(super.request, accountNumber, 3, region);
                                session.setAttribute(SystemConstant.SESSION.CUTLIST, cycleList);

                                this.session.setAttribute("trxSum", new ArrayList());
                                session.removeAttribute("curtabcut");

                                form.setRequiredDoc(false);
                        }
                       
                       
                        String tmp = (String)this.getAccountInfo().get(SystemConstant.COLUMN.DD_PAYMENT);
                       
                        if(!tmp.equals("")&&tmp!=null)
                        {
                                form.setPaymentType(tmp);
                                LoggerService.log.info("DD_PAYMENT:" + tmp);
                        }
                        else
                        {
                                AccountInfomationService vplus = new AccountInfomationServiceImpl();
                                form.setPaymentType(vplus.getPaymentType(this.getAccountNo(), this.getRegion()));
                                LoggerService.log.info("DD_PAYMENT Not Found");
                        }
                       
                        this.calTotalAmount(form.getWorkflowId());

                } catch (Exception ex) {
                        MESSAGE.setMessageType(SystemConstant.MESSAGE.TYPE_ERROR);
                        MESSAGE.setMessageText(ex.toString());
                        LoggerService.log.error("DisputeAction index "+getAccountNo());
                        ex.printStackTrace();
                        throw ex;
                } finally {
                        super.setSessionParam("RESPONSE_MESSAGE", MESSAGE);
                }

                return SUCCESS;
        }

        @Action(value = "perform", results = { @Result(name = SUCCESS, type = "redirect", location = "/pages/workflow/dispute.jsp") })
        public String perform() throws Exception {
                System.out.println("render");
                return SUCCESS;
        }

        @Action(value = "NEW", results = { @Result(name = SUCCESS, type = "redirect", location = "/workflow/history") })
        public String doNew() throws Exception {
                MessageBean MESSAGE = new MessageBean();
                try {

                        ArrayList<DisputeBean> trxList = (ArrayList) session.getAttribute("trxSum2");
                        LoggerService.log.info("TOTAL POST TRANSACTION = " + trxList.size()+" "+getAccountNo());

                        form.setStatus(SystemConstant.STATUS.CLOSE);

                        Iterator iter = trxList.iterator();
                        while (iter.hasNext()) {
                                DisputeBean disputeBean = (DisputeBean) iter.next();
                                disputeBean.setCardNumber(form.getSelectedCardNumber());
                                disputeBean.setCardType(form.getSelectedCardType());
                                disputeBean.setPaymentType(form.getPaymentType());
                        }

                        form.setCusCardNumber(form.getSelectedCardNumber());

                        Workflow bean = super.saveWorkflow(form);

                        form.setWorkflowId(bean.getWorkflowId());

                        sendActionCode(trxList);

                        disputeService.saveTransaction(trxList, bean.getWorkflowId());

                        session.removeAttribute("trxSum2");

                } catch (Exception ex) {
                        MESSAGE.setMessageType(SystemConstant.MESSAGE.TYPE_ERROR);
                        MESSAGE.setMessageText(ex.toString());
                        LoggerService.log.error("DisputeAction doNew "+getAccountNo());
                        ex.printStackTrace();
                        throw ex;
                } finally {
                        super.setSessionParam("RESPONSE_MESSAGE", MESSAGE);
                }

                return SUCCESS;
        }

        @Action(value = "APR", results = { @Result(name = SUCCESS, type = "redirect", location = "/workflow/history") })
        public String doApr() throws Exception {

                ArrayList<DisputeBean> dataList = disputeService.getTransaction(this.getWorkflowId());

                sendActionCode(dataList);

                form.setStatus(SystemConstant.STATUS.CLOSE);
                this.saveWorkflow(form);
                return SUCCESS;
        }

        @Action(value = "PEN", results = { @Result(name = SUCCESS, type = "redirect", location = "/workflow/history") })
        public String doPen() throws Exception {
                System.out.println("do Action = " + form.getStatus());

                MessageBean MESSAGE = new MessageBean();
                try {
                        this.saveWorkflow(form);
                } catch (Exception ex) {
                        MESSAGE.setErrorMessage(ex);
                        LoggerService.log.error("DisputeAction doPen "+getAccountNo());
                        ex.printStackTrace();
                } finally {
                        super.setSessionParam("RESPONSE_MESSAGE", MESSAGE);
                }

                return SUCCESS;
        }

        @Action(value = "REJ", results = { @Result(name = SUCCESS, type = "redirect", location = "/workflow/history") })
        public String doRej() throws Exception {
                return doPen();
        }

        @Action(value = "CAN", results = { @Result(name = SUCCESS, type = "redirect", location = "/workflow/history") })
        public String doCan() throws Exception {
                return doPen();
        }

        @Action(value = "addTransaction", results = { @Result(name = SUCCESS, location = "/pages/workflow/dispute2.jsp") })
        public String doSelectTransaction() throws Exception {

                try {

                        // System.out.println("call >>>>> add transaction");
                        LoggerService.log.info("AddTransaction : doSelectTransaction()"+ " Account number :"+ this.getAccountNo());
                        String markType = request.getParameter("bytype");
                        BigDecimal totalAmount = new BigDecimal(request.getParameter("totalAmount")) ;

                        ArrayList sumlist = (ArrayList) request.getSession().getAttribute("trxSum");
                        if (sumlist.size() > 0) {

                                String[] arr1 = request.getParameterValues("suspenseType");
                                String[] arr2 = request.getParameterValues("reasonCode");
                                String[] arr3 = request.getParameterValues("docreq");

                                for (int ix = 0; ix < sumlist.size(); ix++) {
                                        TransactionBean bean = (TransactionBean) sumlist.get(ix);
                                        bean.setTempVal1(arr1[ix]);
                                        bean.setTempVal2(arr2[ix]);
                                        bean.setTempVal3(arr3[ix]);
                                }
                        }
                       
                        LoggerService.log.info("Get paramether mark type :"+markType);
                        if ((markType.equals("A") /*&& sumlist.size() < 4*/) ||
                            (markType.equals("R") /*&& sumlist.size() < 5*/)) {

                                TransactionHelperService transactionHelper = new TransactionHelperServiceImpl();
                                transactionHelper.markTransaction(this.request, markType);

                                this.calTotalAmount(totalAmount);
                                request.setAttribute("REFRESH", "REFRESH");
                        }
                } catch (Exception ex) {
                        LoggerService.log.error("DisputeAction doSelectTransaction "+getAccountNo());
                        ex.printStackTrace();
                }

                return SUCCESS;
        }

        @Action(value = "submitTransaction", results = { @Result(name = SUCCESS, location = "/pages/workflow/dispute2.jsp") })
        public String doSubmitTransaction() throws Exception {

                try {
                        //System.out.println(">>>>> DO SUBMIT TRANSACTION");

                        String[] suspenses = request.getParameterValues("suspenseType");
                        String[] reasons = request.getParameterValues("reasonCode");
                        String[] docreq = request.getParameterValues("docreq");

                        ArrayList sumlist2 = new ArrayList();
                        ArrayList sumlist = (ArrayList) session.getAttribute("trxSum");

                        for (int ix = 0; ix < sumlist.size(); ix++) {
                                TransactionBean tmm = (TransactionBean) sumlist.get(ix);

                                DisputeBean disputeBean = new DisputeBean();

                                BeanUtils.copyProperties(disputeBean, tmm);

                                disputeBean.setSuspense(suspenses[ix]);
                                disputeBean.setReasonCode(reasons[ix]);
                                disputeBean.setDoc(docreq[ix]);
                                disputeBean.setPaymentType(form.getPaymentType());
                                disputeBean.setBillingCut((String) getAccountInfo().get("CYCLE_CUT"));

                                sumlist2.add(disputeBean);
                        }

                        session.removeAttribute("trxSum");
                        session.setAttribute("trxSum2", sumlist2);

                        request.setAttribute("READY", "Y");

                } catch (Exception ex) {
                        ex.printStackTrace();
                }

                return SUCCESS;
        }

        @Action(value = "query", results = { @Result(name = "success", location = "/pages/workflow/dispute1.jsp") })
        public String queryTransaction() throws Exception {

                try {
                        String cardNumber = request.getParameter("cardNumber");
                        String curtabcut = request.getParameter("curtabcut");
                       
                        LoggerService.log.info("QUERY CARD NUMBER : " + cardNumber);
                        LoggerService.log.info("curtabcut : "+ curtabcut);
                        TransactionHelperService transactionHelper = new TransactionHelperServiceImpl();
                       
                       
                        transactionHelper.queryTransaction(super.request, cardNumber, super.getRegion(), this.getWorkflowTypeId().toString());

                        ArrayList trxList = (ArrayList) request.getSession().getAttribute(SystemConstant.SESSION.TRCUT0);
                        ArrayList<TransactionBean> temlist = (ArrayList) trxList.clone();

                         LoggerService.log.info("QUERY TRANSACTION SIZE: "+trxList.size());
                        // trxList.size());
                        FeeAdjustmentService feeAdjustmentService = new FeeAdjustmentServiceImpl();

                        if (trxList.size() > 0) {

                                CommonsDAO commonDAO = new CommonsDAOImpl();
                                Map binMap = commonDAO.getBinNumberData(this.getAccountNo());

                                Iterator iter = temlist.iterator();
                                while (iter.hasNext()) {
                                        TransactionBean bean = (TransactionBean) iter.next();

                                        if ("C".equals(bean.getDrcr())) {

                                                for (int ix = 0; ix < trxList.size(); ix++) {
                                                        TransactionBean trbean = (TransactionBean) trxList.get(ix);
                                                        // System.out.println(trbean.getDrcr() +
                                                        // "["+bean.getId()+","+trbean.getId()+"]");
                                                        if ((bean.getId() == trbean.getId())) {
                                                                trxList.remove(ix);
                                                                break;
                                                        }
                                                }
                                        }// <<=== END IF
                                        else {

                                                for (int ix = 0; ix < trxList.size(); ix++) {
                                                        TransactionBean trbean = (TransactionBean) trxList.get(ix);
                                                        HashMap map = feeAdjustmentService.getWaiveActionCode(super.getAccountNo(), trbean.getTransactionCode(), "AUTO");

                                                        if (map != null) {
                                                                String actionCode = (String) map.get("ACTION_CODE");
                                                                if (!trbean.getTransactionCode().equals(actionCode)) {
                                                                        trxList.remove(ix);
                                                                        break;
                                                                }
                                                        }

                                                }
                                        }
                                } // end while
                        }// end if
                        request.getSession().setAttribute("curtabcut", curtabcut);
                        request.getSession().setAttribute("trxList", trxList);
                       

                } catch (Exception ex) {
                        LoggerService.log.error("DisputeAction queryTransaction "+getAccountNo());
                        ex.printStackTrace();
                }
                return SUCCESS;
        }

        private void calTotalAmount(BigDecimal workflowId) throws Exception {

                BigDecimal totalAmount = BigDecimal.ZERO;

                String famt = commonsService.getTextByCode(this.getWorkflowTypeId(), "AMOUNT", "FEE");

                BigDecimal fee = new BigDecimal(famt);

                int noTran = 0;
                int feeitem = 0;

                ArrayList sumlist = (ArrayList) session.getAttribute("trxSum");

                if (sumlist != null) {
                        Iterator iter = sumlist.iterator();
                        while (iter.hasNext()) {

                                if (workflowId != null && workflowId.intValue() > 0) {
                                        DisputeBean tmm = (DisputeBean) iter.next();
                                        totalAmount = totalAmount.add(tmm.getAmount());

                                        if (!"MISC".equals(tmm.getSuspense()))
                                                feeitem += 1;

                                } else {
                                        TransactionBean tmm = (TransactionBean) iter.next();
                                        totalAmount = totalAmount.add(tmm.getAmount());
                                        tmm.setTempVal3("Y");
                                        tmm.setTempVal1("DITM");
                                        if (!"MISC".equals(tmm.getTempVal1()))
                                                feeitem += 1;
                                }

                        }

                        noTran = sumlist.size();
                }

                fee = fee.multiply(new BigDecimal(feeitem));

                session.setAttribute(SystemConstant.SESSION.TOTAL_TRANSACTION, noTran);
                session.setAttribute(SystemConstant.SESSION.TOTAL_FEE, fee.doubleValue());
                session.setAttribute(SystemConstant.SESSION.TOTAL_AMOUNT, totalAmount.doubleValue());
        }

        private void sendActionCode(ArrayList trxList) throws Exception {

                String user = (String) this.getAccountInfo().get(SystemConstant.COLUMN.IAM_USERNAME);
                //user = commonsService.getUserFirstname(user);
                userFullname = (String)getAccountInfo().get(SystemConstant.SESSION.USER_FULL_NAME);
               
                List<HashMap> disputeReasonList = (List) session.getAttribute(SystemConstant.SESSION.DISPUTE_REASON);

                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH);

                BigDecimal sumAmt = BigDecimal.ZERO;

                String tranStr = "";

                String cardNumber = "";

                for (int ix = 0; ix < trxList.size(); ix++) {

                        DisputeBean bean = (DisputeBean) trxList.get(ix);
                        // System.out.println("SUSPENSE : " + bean.getSuspense());
                        cardNumber = bean.getCardNumber();

                        String misctext = commonsService.getTextByCode(this.getWorkflowTypeId(), "MESSAGE", bean.getSuspense().substring(0, 1));
                        // #v1.#v2 #v3 จำนวน #v4 บาท เนื่องจาก#v5 วิธีตรวจสอบ#v6
                        // คิดค่าธรรมเนียม #v7 บาทต่อรายการ
                        // #v1.#v2 #v3 จำนวน #v4 บาท เนื่องจาก#v5
                        // ตรวจสอบรายการไม่ตั้งพักยอดโดยไม่คิดค่าธรรมเนียมการตรวจสอบ

                        misctext = misctext.replaceAll("#v1", String.valueOf(ix + 1));

                        String datestr = "[EFF DATE]" + sdf.format(bean.getEffectiveDate()) + " [POST DATE]" + sdf.format(bean.getPostDate());

                        misctext = misctext.replaceAll("#v2", datestr);
                        misctext = misctext.replaceAll("#v3", bean.getMerchant());
                        misctext = misctext.replaceAll("#v4", super.formatMonetary(bean.getAmount()));

                        Iterator iter = disputeReasonList.iterator();
                        while (iter.hasNext()) {
                                HashMap map = (HashMap) iter.next();
                                if (bean.getReasonCode().equals((String) map.get("ID"))) {
                                        misctext = misctext.replaceAll("#v5", (String) map.get("TEXT"));
                                        break;
                                }
                        }

                        String suspenseText = commonsService.getTextByCode(this.getWorkflowTypeId(), "DISPUTE_SUSPENSE", bean.getSuspense());
                        misctext = misctext.replaceAll("#v6", suspenseText);

                        String feeamt = commonsService.getTextByCode(this.getWorkflowTypeId(), "AMOUNT", "FEE");
                        misctext = misctext.replaceAll("#v7", feeamt);

                        sumAmt = sumAmt.add(bean.getAmount());
                        tranStr += misctext + ", ";
                }

                tranStr = tranStr.trim().substring(0, tranStr.length() - 1);

                String message = commonsService.getTextByCode(this.getWorkflowTypeId(), "MESSAGE", "1");
                // ส่งเรื่องปฏิเสธรายการ (#v1) รวมทั้งสิ้น #v2 รายการ เป็นจำนวนเงินรวม
                // #3 บาท #v4 โดย #v5
                message = message.replaceAll("#v1", tranStr);
                message = message.replaceAll("#v2", String.valueOf(trxList.size()));
                message = message.replaceAll("#3", super.formatMonetary(sumAmt));
                message = message.replaceAll("#v4", form.getFreeText());
                message = message.replaceAll("#v5", user+" "+userFullname);

                super.miscNote(message, cardNumber);
        }

        public DisputeForm getForm() {
                return form;
        }

        public void setForm(DisputeForm form) {
                this.form = form;
        }

}
