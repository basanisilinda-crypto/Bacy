"""Run the actual EA state loader/persistence with in-memory MT5 platform stubs."""
from pathlib import Path
import shutil
import subprocess

import pytest


def test_native_state_recovery_preserves_operational_latches(tmp_path):
    compiler = shutil.which("g++")
    if not compiler:
        pytest.skip("C++ compiler required for native state-loader checks")
    source = (Path(__file__).parents[1] / "mt5/XMUltraAITrader.mq5").read_text()
    persist = "void Persist()" + source.split("void Persist()", 1)[1].split('string g_anchor_reason', 1)[0]
    loader = "bool ReadSavedNumber" + source.split("bool ReadSavedNumber", 1)[1].split("void UpdateAnchors", 1)[0]
    harness = r'''
#include <cassert>
#include <cmath>
#include <map>
#include <string>
using string=std::string;
using datetime=long long;
template<class T,unsigned long N> int ArraySize(T (&)[N]) { return N; }
bool g_tester=false,g_state_loaded=false,g_demo_recovered=false,g_emergency=false;
bool g_uncertain=false,g_anchor_block=false,enforced=false,backup_ok=true;
datetime g_day=0,g_week=0,g_cooldown=0;
double g_day_equity=0,g_week_equity=0,g_high_water=0,equity=9700;
int g_losses=0,g_max_losses=3,InpCooldownMinutes=60,backups=0,events=0;
string g_load_error,g_anchor_reason;
std::map<string,double> saved,backup;
const int ACCOUNT_EQUITY=1;
double AccountInfoDouble(int) { return equity; }
bool Finite(double v) { return std::isfinite(v); }
double MathFloor(double v) { return std::floor(v); }
datetime UtcNow() { return 7000000; }
datetime DayStart(datetime t) { return t-t%86400; }
datetime WeekStart(datetime t) { return t-t%604800; }
string Key(const string s) { return s; }
bool GlobalVariableCheck(const string s) { return saved.count(s)>0; }
double GlobalVariableGet(const string s) { return saved.at(s); }
void GlobalVariableSet(const string s,double v) { saved[s]=v; }
void GlobalVariablesFlush() {}
bool LossStopsActive() { return enforced; }
bool BackupRiskState() { backups++; backup=saved; return backup_ok; }
bool ReconcileMissedBoundaries() { return false; }
void QueueEvent(const string,const string) { events++; }
void reset() {
    g_state_loaded=false;g_demo_recovered=false;g_emergency=false;g_uncertain=false;
    g_anchor_block=false;enforced=false;backup_ok=true;backups=0;events=0;equity=9700;
    saved={{"day",(double)DayStart(UtcNow())},{"week",(double)WeekStart(UtcNow())},
        {"dayeq",10000},{"weekeq",11000},{"hwm",12000},{"losses",4},{"cooldown",7000200},
        {"emergency",1},{"uncertain",1},{"anchorblock",0},{"lastseen",6999990}};
}
'''
    harness += persist + loader
    harness += r'''
int main() {
    reset();assert(LoadAnchors());assert(backups==0);assert(g_day_equity==10000);
    assert(g_emergency&&g_uncertain&&g_losses==4);
    reset();saved.erase("dayeq");auto original=saved;
    assert(LoadAnchors());assert(backups==1&&backup==original&&events==1);
    assert(g_state_loaded&&g_demo_recovered&&g_anchor_block);
    assert(g_emergency&&g_uncertain&&g_losses==4&&g_cooldown==7000200);
    assert(g_week_equity==11000&&g_high_water==12000&&g_day_equity==9700);
    assert(saved.at("demorecovered")==1);
    g_demo_recovered=false;g_anchor_block=false;
    assert(LoadAnchors());assert(g_demo_recovered&&g_anchor_block);
    // Strict/live mode never repairs or overwrites a rejected state, even on deinit Persist.
    reset();enforced=true;saved.erase("dayeq");original=saved;
    assert(!LoadAnchors());Persist();assert(saved==original&&backups==0);
    assert(g_load_error.find("dayeq")!=string::npos);
    // Missing or invalid safety latches cannot be inferred away on demo.
    reset();saved.erase("emergency");original=saved;
    assert(!LoadAnchors());Persist();assert(saved==original&&backups==0);
    reset();saved["uncertain"]=NAN;
    assert(!LoadAnchors());assert(!g_state_loaded);
    // Unavailable equity is diagnosed separately from damaged saved state.
    reset();equity=0;original=saved;
    assert(!LoadAnchors());Persist();assert(saved==original);
    assert(g_load_error.find("equity")!=string::npos);
    // Recovery requires successful backup and detects partial state even without day marker.
    reset();saved.erase("day");backup_ok=false;original=saved;
    assert(!LoadAnchors());Persist();assert(saved==original);
    reset();saved.erase("day");assert(LoadAnchors());assert(backups==1&&g_demo_recovered);
    reset();saved["demorecovered"]=2;original=saved;
    assert(!LoadAnchors());Persist();assert(saved==original);
    // Completely fresh state can initialize; no legacy state is silently discarded.
    reset();saved.clear();assert(LoadAnchors());assert(backups==0&&!g_demo_recovered);
    reset();saved.clear();saved["demorecovered"]=1;original=saved;
    assert(!LoadAnchors());Persist();assert(saved==original);
}
'''
    cpp, binary = tmp_path / "loader.cpp", tmp_path / "loader"
    cpp.write_text(harness)
    subprocess.run([compiler, "-std=c++17", "-Wall", "-Wextra", "-Werror", str(cpp), "-o", str(binary)], check=True, capture_output=True, text=True)
    subprocess.run([str(binary)], check=True, capture_output=True, text=True)
