package io.sbs.controller;

import io.sbs.constant.StringConstants;
import io.sbs.constant.UserType;
import io.sbs.dto.TransferPostDTO;
import io.sbs.dto.WorkflowDTO;
import io.sbs.service.AccountService;
import io.sbs.vo.ResultVO;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/acc")
public class AccountController {

	// AccountsRepository accounts = new AccountsRepository();
	//
	// @RequestMapping(value = "/alldata", method = RequestMethod.GET)
	// public String getAll() {
	// System.out.println("Listing sample data");
	// return accounts.getdata();
	// }

	@Autowired
	AccountService accountService;

	@RequestMapping(value = "/transfer", method = RequestMethod.POST)
	public void transfer_funds(HttpServletRequest request,
			@RequestBody TransferPostDTO transferPostDTO) {
		accountService.transfer_funds(transferPostDTO);
	}

	@RequestMapping(value = "/transfer_approve", method = RequestMethod.POST)
	public ResultVO transfer_approve(@RequestBody WorkflowDTO workflowDTO) {
		WorkflowDTO workflowObj = new WorkflowDTO();
		if (workflowDTO.getType().equals(
				StringConstants.WORKFLOW_CRITICAL_TRANSFER)
				&& workflowDTO.getRole() == UserType.Tier2) {

			workflowObj = accountService.approveCriticalTransfer(workflowDTO);
		} else if (workflowDTO.getType().equals(
				StringConstants.WORKFLOW_NON_CRITICAL_TRANSFER)
				&& workflowDTO.getRole() == UserType.Tier1) {
			workflowObj = accountService.approveNonCriticalTransfer(workflowDTO);
		}

		// else if(workflowDTO.getType()=="appointment")
		// workflowObj = appointmentService.createAppointments(workflowDTO);
		return ResultVO.createSuccess(workflowObj);

	}
}