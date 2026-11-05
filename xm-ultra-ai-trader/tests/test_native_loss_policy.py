"""Test actual native policy functions with stubs; not a MetaEditor build."""
from pathlib import Path
import shutil
import subprocess

import pytest


def test_native_loss_policy(tmp_path):
    compiler = shutil.which('g++')
    if not compiler:
        pytest.skip('C++ compiler required')
    source = (Path(__file__).parents[1] / 'mt5/XMUltraAITrader.mq5').read_text()
    policy = source.split('// BEGIN PORTABLE LOSS POLICY', 1)[1]
    policy = policy[policy.index('bool EnforceLossStops'):].split('// END PORTABLE LOSS POLICY', 1)[0]
    gate = source.split('bool AccountGate(string &why)', 1)[1].split('bool CurrentQuote', 1)[0]
    harness = r'''
#include <algorithm>
#include <cassert>
#include <cmath>
#include <string>
using string=std::string;
double MathMin(double a,double b) { return std::min(a,b); }
enum { ACCOUNT_LOGIN, ACCOUNT_TRADE_ALLOWED, ACCOUNT_TRADE_EXPERT, ACCOUNT_EQUITY,
       ACCOUNT_MARGIN_FREE, TERMINAL_CONNECTED, TERMINAL_TRADE_ALLOWED, MQL_TRADE_ALLOWED };
bool demo=true,override_on=true,g_tester=false,g_emergency=false,g_uncertain=false;
bool g_anchor_block=false,g_events_overflow=false,connected=true,permission=true;
long g_account=123,g_cooldown=0;
double equity=9700,margin=9000,g_day_equity=10000,g_week_equity=10000,g_high_water=11000;
double g_daily_pct=1,g_weekly_pct=3,g_drawdown_pct=5;
string g_anchor_reason="missed boundary";
long AccountInfoInteger(int key) { return key==ACCOUNT_LOGIN?g_account:1; }
double AccountInfoDouble(int key) { return key==ACCOUNT_EQUITY?equity:margin; }
bool TerminalInfoInteger(int key) { return key==TERMINAL_CONNECTED?connected:permission; }
bool MQLInfoInteger(int) { return permission; }
bool AllowedAccount() { return true; }
bool Finite(double x) { return std::isfinite(x); }
long UtcNow() { return 100; }
'''
    harness += policy
    harness += '\nbool LossStopsActive() { return EnforceLossStops(override_on,demo,g_tester); }\n'
    harness += '\nbool AccountGate(string &why)' + gate
    harness += r'''
int main() {
    string why;
    g_anchor_block=true; g_cooldown=200; assert(AccountGate(why));
    assert(EntryRiskBudget(9700,.25,2,0,9900,9700,10450,false)==24.25);
    override_on=false; assert(!AccountGate(why)); override_on=true;
    demo=false; assert(!AccountGate(why)); g_anchor_block=false; g_cooldown=0;
    assert(!AccountGate(why)); assert(why.find("Daily")!=string::npos);
    assert(EntryRiskBudget(9700,.25,2,0,9900,9700,10450,true)<0);
    demo=true; g_tester=true; assert(!AccountGate(why)); g_tester=false;
    assert(AccountGate(why));
    g_emergency=true; assert(!AccountGate(why)); g_emergency=false;
    connected=false; assert(!AccountGate(why)); connected=true;
    permission=false; assert(!AccountGate(why)); permission=true;
    g_uncertain=true; assert(!AccountGate(why)); g_uncertain=false;
    g_events_overflow=true; assert(!AccountGate(why)); g_events_overflow=false;
    margin=0; assert(!AccountGate(why)); margin=9000;
    equity=0; assert(!AccountGate(why)); equity=NAN; assert(!AccountGate(why));
    assert(EntryRiskBudget(9700,.25,2,40,9900,9700,10450,false)==8.5);
    assert(EntryRiskBudget(9700,.25,2,50,9900,9700,10450,false)<0);
    assert(EntryRiskBudget(9700,.25,1,10,9900,9700,10450,false)==14.25);
}
'''
    cpp, binary = tmp_path / 'policy.cpp', tmp_path / 'policy'
    cpp.write_text(harness)
    subprocess.run([compiler, '-std=c++17', '-Wall', '-Wextra', '-Werror', str(cpp), '-o', str(binary)], check=True, capture_output=True, text=True)
    subprocess.run([str(binary)], check=True, capture_output=True, text=True)
