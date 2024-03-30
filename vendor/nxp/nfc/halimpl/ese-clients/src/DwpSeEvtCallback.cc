#include "DwpSeEvtCallback.h"
#include "DwpEseUpdater.h"

void DwpSeEvtCallback::evtCallback(__attribute__((unused)) SESTATUS evt) {
  DwpEseUpdater::sendeSEUpdateState(ESE_JCOP_UPDATE_COMPLETED);
  DwpEseUpdater::setDwpEseClientState(ESE_UPDATE_COMPLETED);
  DwpEseUpdater::eSEUpdate_SeqHandler();
  return;
}