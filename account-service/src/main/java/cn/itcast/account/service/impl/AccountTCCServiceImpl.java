package cn.itcast.account.service.impl;

import cn.itcast.account.entity.AccountFreeze;
import cn.itcast.account.mapper.AccountFreezeMapper;
import cn.itcast.account.mapper.AccountMapper;
import cn.itcast.account.service.AccountTCCService;
import io.seata.core.context.RootContext;
import io.seata.rm.tcc.api.BusinessActionContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class AccountTCCServiceImpl implements AccountTCCService {

    @Autowired
    private AccountMapper accountMapper;

    @Autowired
    private AccountFreezeMapper accountFreezeMapper;


    @Override
    @Transactional
    public void deduct(String userId, int money) {
        //获取事务id
        String xid = RootContext.getXID();
        //判断freeze中是否有冻结记录，如果有一定是cancel执行过，我要拒绝业务
        AccountFreeze oldFreeze = accountFreezeMapper.selectById(xid);
        if (oldFreeze!=null){
            //cancel执行过，我要拒绝业务
            return;
        }
        //扣减可用余额
        accountMapper.deduct(userId, money);
        //记录冻结金额，事务状态
        AccountFreeze accountFreeze = new AccountFreeze();
        accountFreeze.setUserId(userId);
        accountFreeze.setFreezeMoney(money);
        accountFreeze.setState(AccountFreeze.State.TRY);
        accountFreeze.setXid(xid);

        accountFreezeMapper.insert(accountFreeze);
    }

    @Override
    public boolean confirm(BusinessActionContext context) {
        //直接获取事务id
        String xid = context.getXid();
        AccountFreeze accountFreeze = accountFreezeMapper.selectById(xid);
        if (accountFreeze==null){
            //已经处理过一次cancel
            return true;
        }
        //根据id删除冻结金额
        int i = accountFreezeMapper.deleteById(xid);
        return i==1;
    }

    @Override
    public boolean cancel(BusinessActionContext context) {
        //查询冻结记录
        String xid = context.getXid();
        String userId = context.getActionContext("userId").toString();
        AccountFreeze accountFreeze = accountFreezeMapper.selectById(xid);
        //空回滚判断，判断accountfreeze是否为null，为null证明try没执行，需要空回滚
        if (accountFreeze==null){
            //证明try没执行，需要空回滚
            accountFreeze = new AccountFreeze();
            accountFreeze.setUserId(userId);
            accountFreeze.setFreezeMoney(0);
            accountFreeze.setState(AccountFreeze.State.CANCEL);
            accountFreeze.setXid(xid);
            accountFreezeMapper.insert(accountFreeze);
            return true;
        }
        //判断幂等性
        if (AccountFreeze.State.CANCEL==accountFreeze.getState()){
            //已经处理过一次cancel
            return true;
        }

        //恢复可用金额
        accountMapper.refund(accountFreeze.getUserId(), accountFreeze.getFreezeMoney());
        //将冻结金额清零，状态改为cancel
        accountFreeze.setFreezeMoney(0);
        accountFreeze.setState(AccountFreeze.State.CANCEL);
        int count=accountFreezeMapper.updateById(accountFreeze);
        return count==1;
    }
}
